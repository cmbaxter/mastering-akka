package com.packt.masteringakka.bookstore.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import dispatch._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import akka.pattern.pipe
import akka.actor.Stash
import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor.Extension
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem

object ElasticsearchApi {
  case class ShardData(total:Int, failed:Int, successful:Int)
  case class IndexingResult(_shards:ShardData, _index:String, _type:String, _id:String, _version:Int, created:Option[Boolean])
  
  case class UpdateScript(inline:String, params:Map[String,Any])
  case class UpdateRequest(script:UpdateScript)
  
  case class SearchHit(_source:JObject)
  case class QueryHits(hits:List[SearchHit])
  case class QueryResponse(hits:QueryHits)   
  
  case class DeleteResult(acknowledged:Boolean)
  
  implicit val formats = Serialization.formats(NoTypeHints)
}

trait ElasticsearchSupport{ me:BookstoreActor =>
  import ElasticsearchApi._
  
  val esSettings = ElasticsearchSettings(context.system)
    
  def indexRoot:String
  def entityType:String 
  
  def baseUrl = s"${esSettings.rootUrl}/${indexRoot}/$entityType"
  
  def queryElasticsearch(query:String)(implicit ec:ExecutionContext):Future[List[JObject]] = {
    val req = url(s"$baseUrl/_search") <<? Map("q" -> query)
    callElasticsearch[QueryResponse](req).
      map(_.hits.hits.map(_._source))
  }
   
  def callElasticsearch[RT : Manifest](req:Req)(implicit ec:ExecutionContext):Future[RT] = {    
    Http(req OK as.String).map(resp => read[RT](resp))
  }   
}

trait ElasticsearchUpdateSupport extends ElasticsearchSupport{ me:ViewBuilder[_] =>
  import ElasticsearchApi._
  
  def updateIndex(id:String, request:AnyRef, version:Option[Long])(implicit ec:ExecutionContext):Future[IndexingResult] = {    
    val urlBase = s"$baseUrl/$id"
    val requestUrl = version match{
      case None => urlBase
      case Some(v) => s"$urlBase/_update?version=$v"
    } 
    
    val req = url(requestUrl) << write(request)
    callAndWait[IndexingResult](req)
  }  
  
  def callAndWait[T <: AnyRef : Manifest](req:Req)(implicit ec:ExecutionContext) = {
    val fut = callElasticsearch[T](req)
    context.become(waitingForEsResult)
    fut pipeTo self    
  }
  
  def clearIndex(implicit ec:ExecutionContext) = {    
    val req = url(s"${esSettings.rootUrl}/${indexRoot}/").DELETE
    callAndWait[DeleteResult](req)
  }
  
  def waitingForEsResult:Receive = {
    case ir:IndexingResult =>
      log.info("Successfully processed a read model projection agaist elasticsearch: {}", ir)
      context.become(handlingEvents)
      unstashAll
      
    case del:DeleteResult =>
      log.info("Successfully deleted index {}", indexRoot)
      context.become(handlingEvents)
      unstashAll      
      
    case akka.actor.Status.Failure(ex) =>
      log.error(ex, "Error calling elasticsearch when building the read model")
      context.become(handlingEvents)
      unstashAll
      
    case other =>
      stash     
  }  
}

class ElasticsearchSettingsImpl(conf:Config) extends Extension{
  val esConfig = conf.getConfig("elasticsearch")
  val host = esConfig.getString("host")
  val port = esConfig.getInt("port")
  val rootUrl = s"http://$host:$port"
}
object ElasticsearchSettings extends ExtensionId[ElasticsearchSettingsImpl] with ExtensionIdProvider { 
  override def lookup = ElasticsearchSettings 
  override def createExtension(system: ExtendedActorSystem) =
    new ElasticsearchSettingsImpl(system.settings.config)
}