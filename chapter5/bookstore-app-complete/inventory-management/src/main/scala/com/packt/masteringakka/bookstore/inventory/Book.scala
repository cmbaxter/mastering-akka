package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import java.util.Date
import com.packt.masteringakka.bookstore.common._
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList

object BookFO{
  def empty = BookFO("", "", "", Nil, 0.0, 0, new Date(0))
}

/**
 * Value object representation of a Book
 */
case class BookFO(id:String, title:String, author:String, tags:List[String], cost:Double, 
  inventoryAmount:Int, createTs:Date, deleted:Boolean = false) extends EntityFieldsObject[String, BookFO]{
  def assignId(id:String) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
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
  override def snapshotAfterCount = Some(5)
  
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
        persist(TagAdded(id, tag))(handleEventAndRespond())
      }
      
    case RemoveTag(tag) =>
      if (!state.tags.contains(tag)){
        log.warning("Cannot remove tag {} to book {}, tag not present", tag, state.id)
        sender() ! stateResponse()
      }
      else{
        persist(TagRemoved(id, tag))(handleEventAndRespond())
      }
      
    case AddInventory(amount) =>
      persist(InventoryAdded(id, amount))(handleEventAndRespond())
                
    case AllocateInventory(orderId, amount) =>
      val event = 
        if (amount > state.inventoryAmount ){
          InventoryBackordered(id, orderId)
        } 
        else{
          InventoryAllocated(id, orderId, amount)           
        }
      persist(event){ ev =>
        ev match{
          case bo:InventoryBackordered =>
            handleEvent(ev)
            sender() ! Failure(FailureType.Validation , InventoryNotAvailError )
          
          case _ =>
            handleEventAndRespond()(ev)
        }
      }             
  }
  
  def isCreateMessage(cmd:Any) = cmd match{
    case cr:CreateBook => true
    case _ => false
  }
  
  override def newDeleteEvent = Some(BookDeleted(id))
  
  
  def handleEvent(event:EntityEvent):Unit = event match {
    case BookCreated(book) => 
      state = book
    case TagAdded(bookId, tag) =>
      state = state.copy(tags = tag :: state.tags)
    case TagRemoved(bookId, tag) =>
      state = state.copy(tags = state.tags.filterNot(_ == tag))
    case InventoryAdded(bookId, amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount + amount)
    case BookDeleted(bookId) =>
      state = state.markDeleted      
    case InventoryAllocated(bookId, orderId, amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount - amount)
    case InventoryBackordered(bookId, orderId) =>
      //nothing to do here
  }
}

/**
 * Companion to the Book entity where the vocab is defined
 */
object Book{ 
  import collection.JavaConversions._
  val EntityType = "book"
  
  object Command{
    case class CreateBook(book:BookFO)
    case class AddTag(tag:String)
    case class RemoveTag(tag:String)
    case class AddInventory(amount:Int)
    case class AllocateInventory(orderId:String, amount:Int)
  }
  
  object Event{
    trait BookEvent extends EntityEvent{def entityType = EntityType}
    case class BookCreated(book:BookFO) extends BookEvent{
      def toDatamodel = {
        val bookDM = Datamodel.Book.newBuilder().
          setId(book.id).
          setTitle(book.title).
          setAuthor(book.author).
          addAllTag(book.tags).
          setCost(book.cost).
          setInventoryAmount(book.inventoryAmount).
          setCreateTs(book.createTs.getTime).
          setDeleted(book.deleted).
          build
          
        Datamodel.BookCreated.newBuilder.
          setBook(bookDM).
          build
      }
    }
    object BookCreated extends DatamodelReader{
      def fromDatamodel = {
        case bc:Datamodel.BookCreated =>
          val bookDm = bc.getBook()
          val book = BookFO(bookDm.getId(), bookDm.getTitle(), bookDm.getAuthor(),
            bookDm.getTagList().toList, bookDm.getCost(), bookDm.getInventoryAmount(),
            new Date(bookDm.getCreateTs()), bookDm.getDeleted())
          BookCreated(book)
      }
    }
    
    case class TagAdded(bookId:String, tag:String) extends BookEvent{
      def toDatamodel = {
        Datamodel.TagAdded.newBuilder().
          setTag(tag).
          setBookId(bookId).
          build
      }
    }
    object TagAdded extends DatamodelReader{
      def fromDatamodel = {
        case ta:Datamodel.TagAdded =>
          TagAdded(ta.getBookId(), ta.getTag())
      }
    }
    
    
    case class TagRemoved(bookId:String, tag:String) extends BookEvent{
      def toDatamodel = {
        Datamodel.TagRemoved.newBuilder().
          setTag(tag).
          setBookId(bookId).
          build
      }      
    }
    
    object TagRemoved extends DatamodelReader{
      def fromDatamodel = {
        case tr:Datamodel.TagRemoved =>
          TagRemoved(tr.getBookId(), tr.getTag())
      }
    }    
    
    case class InventoryAdded(bookId:String, amount:Int) extends BookEvent{
      def toDatamodel = {
        Datamodel.InventoryAdded.newBuilder().
          setAmount(amount).
          setBookId(bookId).
          build
      }
    }
    object InventoryAdded extends DatamodelReader{
      def fromDatamodel = {
        case ia:Datamodel.InventoryAdded =>
          InventoryAdded(ia.getBookId(), ia.getAmount())
      }
    }
    
    case class InventoryAllocated(bookId:String, orderId:String, amount:Int) extends BookEvent{
      def toDatamodel = {
        Datamodel.InventoryAllocated.newBuilder().
          setOrderId(orderId).
          setBookId(bookId).
          setAmount(amount).
          build
      }
    }
    object InventoryAllocated extends DatamodelReader{
      def fromDatamodel = {
        case ia:Datamodel.InventoryAllocated =>
          InventoryAllocated(ia.getBookId(), ia.getOrderId(), ia.getAmount())
      }
    }
    
    case class InventoryBackordered(bookId:String, orderId:String) extends BookEvent{
      def toDatamodel = {
        Datamodel.InventoryBackordered.newBuilder().
          setOrderId(orderId).
          setBookId(bookId).
          build
      }
    }
    object InventoryBackordered extends DatamodelReader{
      def fromDatamodel = {
        case ib:Datamodel.InventoryBackordered =>
          InventoryBackordered(ib.getBookId(), ib.getOrderId())
      }
    }
        
    case class BookDeleted(bookId:String) extends BookEvent{
      def toDatamodel = {
        Datamodel.BookDeleted.newBuilder().
          setBookId(bookId).
          build
          
      }
    }
    object BookDeleted extends DatamodelReader{
      def fromDatamodel = {
        case bd:Datamodel.BookDeleted =>
          BookDeleted(bd.getBookId())
      }
    }
  }

  def props(id:String) = Props(classOf[Book], id)
  
  val BookAlreadyCreated = ErrorMessage("book.alreadyexists", Some("This book has already been created and can not handle another CreateBook request"))
  val InventoryNotAvailError = ErrorMessage("inventory.notavailable", Some("Inventory for an item on an order can not be allocated"))
}