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
import com.packt.masteringakka.bookstore.common.ResumableProjection
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.stream.ActorMaterializer
import akka.persistence.query.EventEnvelope
import akka.cluster.sharding.ClusterSharding
import com.packt.masteringakka.bookstore.common.PersistentEntity
import com.packt.masteringakka.bookstore.common.BookstoreActor
import akka.actor.ActorRef

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
  
  trait SalesOrderCreateInfo{
    def id:String
    def lineItemInfo:List[(String, Int)]
  }
  
  def props = Props[InventoryClerk]
  
  val Name = "inventory-clerk"
}

/**
 * Aggregate root actor for managing the book entities 
 */
class InventoryClerk extends Aggregate[BookFO, Book]{
  import InventoryClerk._
  import com.packt.masteringakka.bookstore.common.PersistentEntity._
  import Book.Command._
 
  
  def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      forwardCommand(id, GetState(id))
          
    case CatalogNewBook(title, author, tags, cost) =>
      log.info("Cataloging new book with title {}", title)
      val id = UUID.randomUUID().toString()
      val fo = BookFO(id, title, author, tags, cost, 0, new Date)
      val command = CreateBook(fo)
      forwardCommand(id, command)
      
    case IncreaseBookInventory(id, amount) =>
      forwardCommand(id, AddInventory(amount, id))
      
    case CategorizeBook(id, tag) =>
      forwardCommand(id, AddTag(tag, id))
      
    case UncategorizeBook(id, tag) =>
      forwardCommand(id, RemoveTag(tag, id))
      
    case RemoveBookFromCatalog(id) =>
      forwardCommand(id, MarkAsDeleted)
      
    case order:SalesOrderCreateInfo =>
      order.lineItemInfo.
        foreach{
          case (bookId, quant) =>
            forwardCommand(bookId, AllocateInventory(order.id, quant, bookId))
        }      
  }
    
  def entityProps = Book.props
}

object InventoryAllocationEventListener{
  val Name = "inventory-allocation-listener"
  def props = Props[InventoryAllocationEventListener]
}

class InventoryAllocationEventListener extends BookstoreActor{
  import InventoryClerk._
  import context.dispatcher
  
  val clerk = context.system.actorSelection(s"/user/${InventoryClerk.Name}")
  
  val projection = ResumableProjection("inventory-allocation", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach{ o =>
    journal.
      eventsByTag("ordercreated", o.getOrElse(0L)).
      runForeach(e => self ! e)
  }  
  
  def receive = {
    case EventEnvelope(offset, pid, seq, order:SalesOrderCreateInfo) =>
      
      //Allocate inventory from each book
      log.info("Received OrderCreated event for order id {}", order.id)
      clerk ! order
      projection.storeLatestOffset(offset)    
  }
}
