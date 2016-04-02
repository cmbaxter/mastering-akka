package com.packt.masteringakka.bookstore.credit

import com.packt.masteringakka.bookstore.Bootstrap
import akka.actor.ActorSystem

object CreditBoot extends Bootstrap{
  def bootup(system:ActorSystem) = {
    system.actorOf(CreditCardTransactionHandler.props, CreditCardTransactionHandler.Name)
    Nil
  }
}