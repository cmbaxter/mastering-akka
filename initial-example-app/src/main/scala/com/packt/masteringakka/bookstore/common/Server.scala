package com.packt.masteringakka.bookstore.common
import com.packt.masteringakka.bookstore.book._
import akka.actor._
import com.typesafe.config.ConfigFactory
import collection.JavaConversions._

/**
 * Main entry point to startup the application
 */
object Server extends App{
  val conf = ConfigFactory.load.getConfig("bookstore")
  val postgresDb = slick.driver.PostgresDriver.api.Database.forConfig("psqldb", conf)  
  implicit val system = ActorSystem("Bookstore", conf)
  import system.dispatcher

  //Boot up each service module from the config and get the endpoints from it
  val endpoints = 
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootup(system))
    
  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)){
    case (endpoint, serv) => 
      println("Adding endpoint: " + endpoint)
      serv.plan(endpoint)
  }
  
  //Adding in the pretend credit card charging service too so that the app works
  server.plan(PretentCreditCardService).run()
  
  def toBootClass(bootPrefix:String) = {
    val clazz = s"com.packt.masteringakka.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
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
