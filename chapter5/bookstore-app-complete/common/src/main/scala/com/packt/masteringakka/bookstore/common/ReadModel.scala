package com.packt.masteringakka.bookstore.common

import akka.actor.Stash
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.stream.ActorMaterializer
import akka.persistence.query.EventEnvelope
import java.util.Date

trait ReadModelObject extends AnyRef{
  def id:String
}

object ViewBuilder{
  import ElasticsearchApi._
  
  sealed trait IndexAction
  case class UpdateAction(id:String, expression:String, params:Map[String,Any]) extends IndexAction
  case class InsertAction(id:String, rm:ReadModelObject) extends IndexAction
  case class NoAction(id:String) extends IndexAction
  case object DeferredCreate extends IndexAction
  case class LatestOffsetResult(offset:Option[Long])
}

trait ViewBuilder[RM <: ReadModelObject] extends BookstoreActor with Stash with ElasticsearchUpdateSupport{
  import context.dispatcher
  import ViewBuilder._
  import ElasticsearchApi._
  import akka.pattern.pipe
  
  //Set up the persistence query to listen for events for the target entity type
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  implicit val materializer = ActorMaterializer()
  
  val resumableProjection = ResumableProjection(projectionId, context.system)
  resumableProjection.
    fetchLatestOffset.
    map(LatestOffsetResult.apply).    
    pipeTo(self)
  
  def projectionId:String
  
  def receive = handlingEvents
  
  def actionFor(id:String, event:Any):IndexAction
  
  def handlingEvents:Receive = {
    case LatestOffsetResult(o) =>
      val offset = o.getOrElse(0L)
      if (offset == 0){
        clearIndex
      }
      
      val offsetDate = new Date(offset)
      val eventsSource = journal.eventsByTag(entityType, offset)
      eventsSource.runForeach(self ! _)      
      log.info("Starting up view builder for entity {} with offset time of {}", entityType, offsetDate)
            
    case env:EventEnvelope =>
      val updateProjection:PartialFunction[util.Try[IndexingResult], Unit] = {
        case tr =>
          resumableProjection.storeLatestOffset(env.offset)
      }
      val id = env.persistenceId.toLowerCase().drop(entityType.length() + 1)   
      actionFor(id, env.event) match {
        case i:InsertAction =>
          updateIndex(i.id, i.rm, None).
            andThen(updateProjection)
          
        case u:UpdateAction =>
          updateDocumentField(u.id, env.sequenceNr - 1, u.expression, u.params).
            andThen(updateProjection)
          
        case NoAction(id) =>
          updateDocumentField(id, env.sequenceNr - 1, "", Map.empty).
            andThen(updateProjection)
        
        case DeferredCreate =>
          //Nothing happening here          
      }
  }
 
  
  def updateDocumentField(id:String, seq:Long, expression:String, params:Map[String,Any]) = {
    val request = UpdateRequest(UpdateScript(s"ctx._source.${expression}", params))
    updateIndex(id, request, Some(seq))    
  }  
  
}