package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.Bootstrap
import akka.actor.ActorSystem
import io.netty.channel.ChannelHandler.Sharable

/**
 * Bootup for the inventory management sub domain model
 */
class InventoryBoot extends Bootstrap{

  def bootup(system:ActorSystem) = {
    import system.dispatcher    
    val bookManager = system.actorOf(BookManager.props, BookManager.Name)    
    List(new BookEndpoint(bookManager))
  }
}