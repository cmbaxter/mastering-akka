package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.Bootstrap
import akka.actor.ActorSystem

object OrderBoot extends Bootstrap {

  def bootup(implicit system:ActorSystem) = {
    import system.dispatcher
    val salesHandler = system.actorOf(SalesOrderHandler.props, SalesOrderHandler.Name)
    List(new SalesOrderEndpoint(salesHandler))
  }
}