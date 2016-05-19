package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookstoreActor
import akka.actor.Props
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.common.ServiceResult
import akka.util.Timeout
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.FullResult
import java.util.Date
import com.packt.masteringakka.bookstore.common.EntityLookupDelegate

/**
 * Companion to the InventoryClerk actor where the vocab is defined 
 */
object InventoryClerk{
  //Query operations
  case class FindBook(id:Int)
  case class FindBooksByTags(tags:Seq[String])
  case class FindBooksByAuthor(author:String)
  
  //Command operations
  case class CatalogNewBook(title:String, author:String, tags:List[String], cost:Double)
  case class CategorizeBook(bookId:Int, tag:String)
  case class UncategorizeBook(bookId:Int, tag:String)
  case class IncreaseBookInventory(bookId:Int, amount:Int)
  case class RemoveBookFromCatalog(id:Int)
  
  def props = Props[InventoryClerk]
  
  val Name = "inventory-clerk"
}

/**
 * Aggregate root actor for managing the book entities 
 */
class InventoryClerk extends BookstoreActor with EntityLookupDelegate[BookVO]{
  import InventoryClerk._
  import com.packt.masteringakka.bookstore.common.EntityActor._
  import context.dispatcher
  val repo = new BookRepository
  
  def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      book.forward(GetValueObject)
      
    case FindBooksByTags(tags) =>
      log.info("Finding books for tags {}", tags)
      val result = multiBookLookup(repo.findBookIdsByTags(tags))          
      pipeResponse(result)
      
    case FindBooksByAuthor(author) =>
      log.info("Finding books for author {}", author)
      val result = multiBookLookup(repo.findBookIdsByAuthor(author))
      pipeResponse(result)  
      
    case CatalogNewBook(title, author, tags, cost) =>
      log.info("Cataloging new book with title {}", title)
      val vo = BookVO(0, title, author, tags, cost, 0, new Date, new Date)
      persistOperation(vo.id, vo)
      
    case IncreaseBookInventory(id, amount) =>
      persistOperation(id, Book.AddInventory(amount))
      
    case CategorizeBook(id, tag) =>
      persistOperation(id, Book.AddTag(tag))
      
    case UncategorizeBook(id, tag) =>
      persistOperation(id, Book.RemoveTag(tag))
      
    case RemoveBookFromCatalog(id) =>
      persistOperation(id, Delete)
  }
  
  def multiBookLookup(f: => Future[Vector[Int]]) = {
    for{
      ids <- f
      bookActors = ids.map(lookupOrCreateChild)
      vos <- Future.traverse(bookActors)(askForVo)
    } yield{
      FullResult(vos.flatMap(_.toOption))
    }    
  }  
  
  def entityProps(id:Int) = Book.props(id)
}
