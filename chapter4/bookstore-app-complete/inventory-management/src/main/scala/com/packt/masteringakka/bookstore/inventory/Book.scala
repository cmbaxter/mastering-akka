package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import akka.actor.Stash
import java.util.Date
import akka.actor.ReceiveTimeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.EntityFieldsObject
import com.packt.masteringakka.bookstore.common.EntityActor
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.ErrorMessage
import com.packt.masteringakka.bookstore.common.PersistentEntity
import com.packt.masteringakka.bookstore.common.EntityEvent

object BookFO{
  def empty = BookFO("", "", "", Nil, 0.0, 0, new Date(0), new Date(0))
}

/**
 * Value object representation of a Book
 */
case class BookFO(id:String, title:String, author:String, tags:List[String], cost:Double, 
  inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false) extends EntityFieldsObject[String, BookFO]{
  def assignId(id:String) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the Book entity where the vocab is defined
 */
private [bookstore] object Book{
  
  object Command{
    case class CreateBook(book:BookFO)
    case class AddTag(tag:String)
    case class RemoveTag(tag:String)
    case class AddInventory(amount:Int)
    case class AllocateInventory(orderId:Int, amount:Int)
  }
  
  object Event{
    case class BookCreated(book:BookFO) extends EntityEvent
    case class TagAdded(tag:String) extends EntityEvent
    case class TagRemoved(tag:String) extends EntityEvent
    case class InventoryAdded(amount:Int) extends EntityEvent
    case class InventoryAllocated(orderId:Int, amount:Int) extends EntityEvent
    case class InventoryBackordered(orderId:Int) extends EntityEvent
    case class BookDeleted(id:String) extends EntityEvent
  }

  def props(id:String) = Props(classOf[Book], id)
  
  def BookAlreadyCreated = ErrorMessage("book.alreadyexists", Some("This book has already been created and can not handle another CreateBook request"))
}

/**
 * Entity class representing a Book within the bookstore app
 */
private[inventory] class Book(id:String) extends PersistentEntity[BookFO](id){
  import Book._
  import Command._
  import Event._
  import PersistentEntity._
  
  def initialState = BookFO.empty
  
  def additionalCommandHandling:Receive = {
    case CreateBook(book) =>
      //Don't allow if not the initial state
      if (state != initialState){
        sender() ! Failure(FailureType.Validation, BookAlreadyCreated)
      }
      else{
        persist(BookCreated(book))(handleEventAndRespond())
      }
      
    case AddTag(tag) =>      
      if (state.tags.contains(tag)){
        log.warning("Not adding tag {} to book {}, tag already exists", tag, state.id)
        sender() ! stateResponse()
      }
      else{
        persist(TagAdded(tag))(handleEventAndRespond())
      }
      
    case RemoveTag(tag) =>
      if (!state.tags.contains(tag)){
        log.warning("Cannot remove tag {} to book {}, tag not present", tag, state.id)
        sender() ! stateResponse()
      }
      else{
        persist(TagRemoved(tag))(handleEventAndRespond())
      }
      
    case AddInventory(amount) =>
      persist(InventoryAdded(amount))(handleEventAndRespond())
      
    
      
    /*case Event(AllocateInventory(amount), InitializedData(fo)) =>
      requestFoForSender
      persist(fo, repo.allocateInventory(fo.id, amount), _ => fo.copy(inventoryAmount = fo.inventoryAmount - amount))*/       
  }
  
  override def newDeleteEvent = Some(BookDeleted(id))
  
  
  def handleEvent(event:EntityEvent):Unit = event match {
    case BookCreated(book) => 
      state = book
    case TagAdded(tag) =>
      state = state.copy(tags = tag :: state.tags)
    case TagRemoved(tag) =>
      state = state.copy(tags = state.tags.filterNot(_ == tag))
    case InventoryAdded(amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount + amount)
    case BookDeleted(id) =>
      state = state.markDeleted
  }
}