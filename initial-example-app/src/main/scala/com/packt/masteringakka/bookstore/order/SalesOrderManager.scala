package com.packt.masteringakka.bookstore.order

import akka.actor._
import com.packt.masteringakka.bookstore.common.BookStoreActor


object SalesOrderManager{
  val Name = "order-manager"
  def props = Props[SalesOrderManager]
}

class SalesOrderManager extends BookStoreActor{
  def receive = {
    case req:CreateOrder =>
      log.info("Creating new sales order processor and forwarding request")
      val proc = context.actorOf(SalesOrderProcessor.props)
      proc forward req
      
    //TODO: Handle lookup cases including lookup by book and lookup for user
  }
}

