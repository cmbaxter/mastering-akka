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
import org.json4s.ext.EnumNameSerializer
import com.packt.masteringakka.bookstore.credit.CreditTransactionStatus
import scala.concurrent.ExecutionContext

/**
 * Main entry point to startup the application
 */
object Server extends App{
  val conf = ConfigFactory.load.getConfig("bookstore")
  val postgresDb = slick.driver.PostgresDriver.api.Database.forConfig("psqldb", conf)  
  implicit val system = ActorSystem("Bookstore", conf)
  import system.dispatcher

  val endpoints:List[BookstorePlan] = List(BookBoot, UserBoot, CreditBoot, OrderBoot).flatMap(_.bootup(system))
  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)){
    case (endpoint, serv) => 
      println("Adding endpoint: " + endpoint)
      serv.plan(endpoint)
  }
  
  //Adding in the pretent credit card charging service too so that the app works
  server.plan(PretentCreditCardService).run()       
}

/**
 * Base trait for the endpoints in the bookstore app
 */
trait BookstorePlan extends async.Plan with ServerErrorResponse{
  import concurrent.duration._
  
  implicit val ec:ExecutionContext
  implicit val endpointTimeout = Timeout(10 seconds)
  implicit val formats = Serialization.formats(NoTypeHints) + new EnumNameSerializer(CreditTransactionStatus)
  
  /**
   * Extractor for matching on a path element that is an Int
   */
  object IntPathElement{
    
    /**
     * Unapply to see if the path element is an Int
     * @param str The string path element to check
     * @return an Option that will be None if not an Int and a Some if an Int
     */
    def unapply(str:String) = util.Try(str.toInt).toOption
  }
  
  /**
   * Generic http response handling method to interpret the result of a Future into how to respond
   * @param f The Future to use the result from when responding
   * @param resp The responder to respond with
   */
  def respond(f:Future[Any], resp:unfiltered.Async.Responder[HttpResponse]) = {

    f.onComplete{
      
      //Got a good result that we can respond with as json
      case util.Success(FullResult(b:AnyRef)) => 
        resp.respond(asJson(ApiResponse(ApiResonseMeta(Ok.code), Some(b))))
        
      //Got an EmptyResult which will become a 404 with json indicating the not found
      case util.Success(EmptyResult) =>
        resp.respond(asJson(ApiResponse(ApiResonseMeta(NotFound.code, Some(ErrorMessage("notfound")))), NotFound))  
        
      //Got a Failure.  Will either be a 400 for a validation fail or a 500 for everything else
      case util.Success(fail:Failure) =>
        val status = fail.failType match{
          case FailureType.Validation => BadRequest
          case _ => InternalServerError
        }
        val apiResp = ApiResponse(ApiResonseMeta(status.code, Some(fail.message)))
        resp.respond(asJson(apiResp, status))    
        
      //Got a Success for a result type that is not a ServiceResult.  Respond with an unexpected exception
      case util.Success(x) =>
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        resp.respond(asJson(apiResp, InternalServerError))
             
      //The Future failed, so respond with a 500
      case util.Failure(ex) => 
        val apiResp = ApiResponse(ApiResonseMeta(InternalServerError.code, Some(ServiceResult.UnexpectedFailure )))
        resp.respond(asJson(apiResp, InternalServerError))     
    }
  }
  
  /**
   * Creates and returns an Unfiltered ResponseFunction to respond as json 
   * @param apiResp The api response to respond with
   * @param status A Status to respond with, defaulting to Ok if not supplied
   * @return An Unfiltered ResponseFunction
   */
  def asJson[T <: AnyRef](apiResp:ApiResponse[T], status:Status = Ok) = {
    val ser = write(apiResp)          
    status ~> JsonContent ~> ResponseString(ser)    
  }
  
  /**
   * Parses the supplied json String into a type specified by T
   * @param json The json to parse into type T
   * @return an instance of T
   */
  def parseJson[T <: AnyRef: Manifest](json:String) = read[T](json)     
}

/**
 * Base actor definition for other actors in the bookstore app to extend from
 */
trait BookStoreActor extends Actor with ActorLogging{
  import akka.pattern.pipe
  import context.dispatcher
  
  //PF to be used with the .recover combinator to convert an exception on a failed Future into a
  //Failure ServiceResult
  private val toFailure:PartialFunction[Throwable, ServiceResult[Nothing]] = {
    case ex => Failure(FailureType.Service, ServiceResult.UnexpectedFailure, Some(ex))
  }
  
  /**
   * Pipes the response from a request to a service actor back to the sender, first
   * converting to a ServiceResult per the contract of communicating with a bookstore service
   * @param f The Future to map the result from into a ServiceResult
   */
  def pipeResponse[T](f:Future[T]):Unit = 
    f.
      map{
        case o:Option[_] => ServiceResult.fromOption(o) 
        case f:Failure => f
        case other => FullResult(other)
      }.
      recover(toFailure). 
      pipeTo(sender())
}

/**
 * Trait that defines a class that will boot up actors from within a specific services module
 */
trait Bootstrap{
  
  /**
   * Books up the actors for a service module and returns the service endpoints for that
   * module to be included in the Unfiltered server as plans
   * @param system The actor system to boot actors into
   * @return a List of BookstorePlans to add as plans into the server
   */
  def bootup(system:ActorSystem):List[BookstorePlan]
}

/**
 * Base dao to use for other daos in the bookstore app
 */
trait BookstoreDao{
  import slick.driver.PostgresDriver.api._
  val db = Server.postgresDb 

  /**
   * Defines some helpers to use in daos
   */
  object DaoHelpers{
    
    /**
     * Adds a method to easily convert from util.Date to sql.Date 
     */
    implicit class EnhancedDate(date:Date){
      
      /**
       * Converts from the date suplied in the constructor into a sql.Date
       * @return a sql.Date 
       */
      def toSqlDate = new java.sql.Date(date.getTime) 
    }
  }  
  
  /**
   * Gets a select statement to use to select the last id val for a serial id field in Postgres
   * @param table The name of the table to get the last id from
   * @return a DBIOAction used to select the last id val
   */
  def lastIdSelect(table:String) = sql"select currval('#${table}_id_seq')".as[Int]
}
