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
import com.typesafe.config.ConfigFactory
import java.util.Date
import com.packt.masteringakka.bookstore.user.UserBoot
import com.packt.masteringakka.bookstore.order.OrderBoot
import com.packt.masteringakka.bookstore.credit.CreditBoot

object Server extends App{
  val conf = ConfigFactory.load.getConfig("bookstore")
  val postgresDb = slick.driver.PostgresDriver.api.Database.forConfig("psqldb", conf)  
  implicit val system = ActorSystem("Bookstore", conf)
  import system.dispatcher

  val endpoints:List[BookstorePlan] = List(BookBoot, UserBoot, CreditBoot, OrderBoot).flatMap(_.bootup)
  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)){
    case (endpoint, serv) => 
      println("Adding endpoint: " + endpoint)
      serv.plan(endpoint)
  }
  
  //Adding in the pretent credit card charging service too so that the app works
  server.plan(PretentCreditCardService).run()       
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
      case util.Success(FullResult(b:AnyRef)) => 
        respondJson(ApiResponse(ApiResonseMeta(Ok.code), Some(b)), resp)
          
      case util.Success(EmptyResult) =>
        respondJson(ApiResponse(ApiResonseMeta(NotFound.code, Some(ErrorMessage("notfound")))), resp, NotFound)  
        
      case util.Success(fail:Failure) =>
        val status = fail.failType match{
          case ValidationFailure => BadRequest
          case _ => InternalServerError
        }
        val apiResp = ApiResponse(ApiResonseMeta(status.code, Some(fail.message)))
        respondJson(apiResp, resp, status)    
        
      case util.Success(x) =>
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        respondJson(apiResp, resp, InternalServerError)
                          
      case util.Failure(ex) => 
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        respondJson(apiResp, resp, InternalServerError)     
    }
  }
  
  def respondJson[T <: AnyRef](apiResp:ApiResponse[T], resp:unfiltered.Async.Responder[HttpResponse], status:Status = Ok) = {
    val ser = write(apiResp)          
    resp.respond(status ~> JsonContent ~> ResponseString(ser))    
  }
  
  def extractBody[T <: AnyRef: Manifest](f: => String) = {
    val body = f
    read[T](body)   
  }
}

trait BookStoreActor extends Actor with ActorLogging{
  import akka.pattern.pipe
  import context.dispatcher
  
  private val toFailure:PartialFunction[Throwable, ServiceResult[Nothing]] = {
    case ex => Failure(ServiceFailure, ServiceResult.UnexpectedFailure, Some(ex))
  }
  
  def pipeResponse[T](f:Future[T]) = 
    f.
      map{
        case o:Option[_] => ServiceResult.fromOption(o) 
        case f:Failure => f
        case other => FullResult(other)
      }.
      recover(toFailure). 
      pipeTo(sender())
}


trait Bootstrap{
  def bootup(implicit system:ActorSystem):List[BookstorePlan]
}

trait BookstoreDao{
  import slick.driver.PostgresDriver.api._
  val db = Server.postgresDb 

  object DaoHelpers{
    implicit class EnhancedDate(date:Date){
      def toSqlDate = new java.sql.Date(date.getTime) 
    }
  }  
  
  def lastIdSelect(table:String) = sql"select currval('#${table}_id_seq')".as[Int]
}
