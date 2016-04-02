package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.Bootstrap
import akka.actor.ActorSystem

object OrderBoot extends Bootstrap {

  def bootup(system:ActorSystem) = {
    import system.dispatcher
    val salesHandler = system.actorOf(SalesOrderManager.props, SalesOrderManager.Name)
    List(new SalesOrderEndpoint(salesHandler))
  }
}