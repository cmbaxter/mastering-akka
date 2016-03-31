package com.packt.masteringakka.bookstore
import com.packt.masteringakka.bookstore.book._
import akka.actor._
import unfiltered.netty._
import akka.util.Timeout
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import unfiltered.request._
import unfiltered.response._
import scala.concurrent.Future
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import unfiltered.response.JsonContent
import unfiltered.response.NotFound
import io.netty.handler.codec.http.HttpResponse
import scala.reflect.ClassTag

object Server extends App{
  implicit val system = ActorSystem("Bookstore")
  import system.dispatcher

  val endpoints:List[BookstorePlan] = List(BookBoot).flatMap(_.bootup)
  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)){
    case (endpoint, serv) => 
      println("Adding endpoint: " + endpoint)
      serv.plan(endpoint)
  }
  
  server.run()
  
  //Force init of db
  PostgresDB.db.executor 
    
}

trait BookstorePlan extends async.Plan with ServerErrorResponse{
  import concurrent.duration._
  
  val system:ActorSystem
  implicit val endpointTimeout = Timeout(10 seconds)
  implicit val formats = Serialization.formats(NoTypeHints)
  
  object IntPathElement{
    def unapply(str:String) = util.Try(str.toInt).toOption
  }
  
  def respond(f:Future[Any], resp:unfiltered.Async.Responder[HttpResponse]) = {
    import system.dispatcher
    f.onComplete{
      case util.Success(Some(b:AnyRef)) => 
        val ser = write(b)          
        resp.respond(JsonContent ~> ResponseString(ser))
          
      case util.Success(None) =>
        resp.respond(NotFound)  
        
      case util.Success(v:Vector[_]) =>
        val ser = write(v)          
        resp.respond(JsonContent ~> ResponseString(ser))        
        
      case util.Success(b:AnyRef) => 
        val ser = write(b)          
        resp.respond(JsonContent ~> ResponseString(ser))
        
      case util.Success(x) =>
        resp.respond(InternalServerError ~> ResponseString(s"Cannot serialize $x to json"))        
          
      case util.Failure(ex) => 
        resp.respond(InternalServerError ~> ResponseString(ex.getMessage))      
    }
  }
  
  def extractBody[T <: AnyRef: Manifest](f: => String) = {
    val body = f
    read[T](body)   
  }
}

trait Bootstrap{
  def bootup(implicit system:ActorSystem):List[BookstorePlan]
}
