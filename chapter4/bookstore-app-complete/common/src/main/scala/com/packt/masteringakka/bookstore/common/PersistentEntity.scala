package com.packt.masteringakka.bookstore.common

import akka.persistence.PersistentActor
import akka.actor.ReceiveTimeout
import akka.actor.ActorLogging
import scala.reflect.ClassTag
import akka.actor.Props

/**
 * Marker trait for something that is an event generated as the result of a command
 */
trait EntityEvent{}

object PersistentEntity{
  case object GetState
  case object MarkAsDeleted
  case object PassivateEntity
  
  
}

/**
 * Base class for the Event Sourced entities to extend from
 */
abstract class PersistentEntity[FO <: EntityFieldsObject[String, FO]](id:String) extends PersistentActor with ActorLogging{
  import PersistentEntity._
  import concurrent.duration._
    
  val entityType = getClass.getSimpleName
  override def persistenceId = s"$entityType-$id"
  
  //Using this scheduled task as the passivation mechanism
  context.setReceiveTimeout(1 minute)
  var state:FO = initialState
  
  def receiveRecover = standardRecover orElse customRecover
  
  def standardRecover:Receive = {
    case ev:EntityEvent => 
      log.info("Recovering persisted event: {}", ev)
      handleEvent(ev)
  }
  
  def customRecover:Receive = PartialFunction.empty
  
  def receiveCommand = standardCommandHandling orElse additionalCommandHandling 
    
  def standardCommandHandling:Receive = {
    case ReceiveTimeout =>
      //Can't just stop, and can't do a poison pill either, so send a custom
      //stop message back into the mailbox to anything else can be processed
      self ! PassivateEntity
      
    case PassivateEntity =>
      log.info("{} entity with id {} is being passivated due to inactivity", entityType, id)
      context stop self    
    
    //Don't allow actions on deleted entities
    case any if state.deleted =>
      log.warning("Not allowing action {} on a deleted entity with id {}", any, id)
      sender() ! stateResponse()
            
    case GetState =>
      sender ! stateResponse()
      
    case MarkAsDeleted =>
      newDeleteEvent match{
        case None =>
          log.info("The entity type {} does not support deletion, ignoring delete request", entityType)
          
        case Some(event) =>
          persist(event)(handleEventAndRespond(false))
      }
  }
  
  def newDeleteEvent:Option[EntityEvent] = None
  
  def initialState:FO
  
  def stateResponse(respectDeleted:Boolean = true) = 
    if (state == initialState) EmptyResult
    else if (respectDeleted && state.deleted) EmptyResult
    else FullResult(state)
  
  def additionalCommandHandling:Receive
  
  def handleEvent(event:EntityEvent):Unit
  
  def handleEventAndRespond(respectDeleted:Boolean = true)(event:EntityEvent):Unit = {
    handleEvent(event)
    sender() ! stateResponse(respectDeleted)
  }  
}

abstract class PersistentEntityFactory[FO <: EntityFieldsObject[String, FO], E <: PersistentEntity[FO] : ClassTag] extends BookstoreActor{
  def lookupOrCreateChild(id:String) = {
    val name = entityActorName(id)
    context.child(name).getOrElse{
      log.info("Creating new {} actor to handle a request for id {}", entityName, id)
      context.actorOf(entityProps(id), name)
    }
  }
  
  def forwardCommand(id:String, msg:Any){
    val entity = lookupOrCreateChild(id)
    entity.forward(msg)    
  }
    
  def entityProps(id:String):Props
  
  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }
  private def entityActorName(id:String) = {    
    s"${entityName.toLowerCase}-$id"  
  }
 
}