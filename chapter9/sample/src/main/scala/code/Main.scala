package code

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.conductr.bundlelib.akka.{ Env, StatusService }
import com.typesafe.conductr.lib.akka.ConnectionContext
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext

object Main extends App with SprayJsonSupport with DefaultJsonProtocol with Directives {
  // getting bundle configuration from Conductr
  val config = Env.asConfig
  val systemName = sys.env.getOrElse("BUNDLE_SYSTEM", "StandaloneSystem")
  val systemVersion = sys.env.getOrElse("BUNDLE_SYSTEM_VERSION", "1")

  // configuring the ActorSystem
  implicit val system = ActorSystem(s"$systemName-$systemVersion", config.withFallback(ConfigFactory.load()))

  // setting up some machinery
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val cc = ConnectionContext()

  val httpServerCfg = system.settings.config.getConfig("helloworld")

  final case class HelloWorldResponse(msg: String)

  final case class Person(name: String, age: Int)

  implicit val helloWorldJsonFormat = jsonFormat1(HelloWorldResponse)
  implicit val personJsonFormat = jsonFormat2(Person)

  def completeWithHello = extractMethod(method => complete(HelloWorldResponse(s"${method.value} Hello World!")))

  def route: Route =
    logRequestResult("chapter9-sample-helloworld") {
      path("helloworld") {
        (get & pathEnd)(completeWithHello) ~
          (put & pathEnd)(completeWithHello) ~
          (patch & pathEnd)(completeWithHello) ~
          (delete & pathEnd)(completeWithHello) ~
          (options & pathEnd)(completeWithHello)
      } ~
        path("person") {
          (post & pathEnd & entity(as[Person])) { person =>
            complete(s"Received: $person")
          } ~
            (get & pathEnd) {
              complete(Person("John Doe", 40))
            }
        }
    }

  (for {
    _ <- Http().bindAndHandle(route, interface = httpServerCfg.getString("ip"), port = httpServerCfg.getInt("port"))
    _ <- StatusService.signalStartedOrExit()
  } yield ()).recover {
    case cause: Throwable =>
      log.error(cause, "Failure while launching HelloWorld")
      StatusService.signalStartedOrExit()
  }
}