package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.Bootstrap
import akka.actor.ActorSystem

object UserBoot extends Bootstrap{

  def bootup(system:ActorSystem) = {
    import system.dispatcher
    
    val userManager = system.actorOf(UserManager.props, UserManager.Name)
    List(new UserEndpoint(userManager))
  }
}