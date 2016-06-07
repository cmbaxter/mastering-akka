package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import com.packt.masteringakka.bookstore.common.ServiceResult
import akka.util.Timeout
import scala.concurrent.Future
import java.util.Date
import java.util.UUID
import com.packt.masteringakka.bookstore.common.Aggregate
import akka.pattern.ask
import com.packt.masteringakka.bookstore.common.PersistentEntity.GetState
import com.packt.masteringakka.bookstore.common.PersistentEntity.MarkAsDeleted
import scala.concurrent.duration.DurationInt

/**
 * Companion to the InventoryClerk actor where the vocab is defined 
 */
object InventoryClerk{
  case class FindBook(id:String)
  
  //Command operations
  case class CatalogNewBook(title:String, author:String, tags:List[String], cost:Double)
  case class CategorizeBook(bookId:String, tag:String)
  case class UncategorizeBook(bookId:String, tag:String)
  case class IncreaseBookInventory(bookId:String, amount:Int)
  case class RemoveBookFromCatalog(id:String)
  
  //Events
  case class OrderCreated(id:String, books:List[(String,Int)])
  case class InventoryAllocated(orderId:String)
  case class InventoryBackOrdered(orderId:String)
  
  def props = Props[InventoryClerk]
  
  val Name = "inventory-clerk"
}

/**
 * Aggregate root actor for managing the book entities 
 */
class InventoryClerk extends Aggregate[BookFO, Book]{
  import InventoryClerk._
  import com.packt.masteringakka.bookstore.common.PersistentEntity._
  import context.dispatcher
  import Book.Command._
  
  //Listen for the OrderCreatd event
  context.system.eventStream.subscribe(self, classOf[OrderCreated])
  
  def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      forwardCommand(id, GetState)
          
    case CatalogNewBook(title, author, tags, cost) =>
      log.info("Cataloging new book with title {}", title)
      val id = UUID.randomUUID().toString()
      val fo = BookFO(id, title, author, tags, cost, 0, new Date)
      val command = CreateBook(fo)
      forwardCommand(id, command)
      
    case IncreaseBookInventory(id, amount) =>
      forwardCommand(id, AddInventory(amount))
      
    case CategorizeBook(id, tag) =>
      forwardCommand(id, AddTag(tag))
      
    case UncategorizeBook(id, tag) =>
      forwardCommand(id, RemoveTag(tag))
      
    case RemoveBookFromCatalog(id) =>
      forwardCommand(id, MarkAsDeleted)
      
    case OrderCreated(id, lineItems) =>
      import akka.pattern.ask
      import concurrent.duration._
      implicit val timeout = Timeout(5 seconds)
      
      //Allocate inventory from each book
      log.info("Received OrderCreated event for order id {}", id)
      val futs = 
        lineItems.
          map{
            case (bookId, quant) =>
              val f = (lookupOrCreateChild(bookId) ? AllocateInventory(id, quant)).mapTo[ServiceResult[BookFO]]
              f.filter(_.isValid)
          }
      
      //If we get even one failure, consider it backordered
      Future.sequence(futs).
        map{ _ =>
          log.info("Inventory available for order {}", id)
          InventoryAllocated(id)        
        }.
        recover{
          case ex => 
            log.warning("Inventory back ordered for order {}", id)
            InventoryBackOrdered(id)
        }.
        foreach(context.system.eventStream.publish)    
  }
    
  def entityProps(id:String) = Book.props(id)
}
