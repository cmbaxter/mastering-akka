package com.packt.masteringakka.bookstore.common

import scala.concurrent.ExecutionContext
import com.typesafe.conductr.lib.akka.ConnectionContext
import com.typesafe.conductr.bundlelib.akka.LocationService
import com.typesafe.conductr.bundlelib.scala.CacheLike
import com.typesafe.conductr.bundlelib.scala.URI


case class ServiceLookupResult(name:String, uriOpt:Option[java.net.URI])
trait ServiceLookup {
  def lookupService(serviceName:String, cache:CacheLike)(implicit ec:ExecutionContext, cc:ConnectionContext) = {
    
    LocationService.
      lookup(serviceName, URI("http://localhost:8080/"), cache).
      map(opt => ServiceLookupResult(serviceName, opt))
  }
}