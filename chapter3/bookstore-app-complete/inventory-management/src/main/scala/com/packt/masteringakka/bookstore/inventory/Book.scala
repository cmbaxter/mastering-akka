package com.packt.masteringakka.bookstore.inventory

import akka.actor.Props
import akka.actor.Stash
import java.util.Date
import akka.actor.ReceiveTimeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.common.ValueObject
import com.packt.masteringakka.bookstore.common.EntityActor

/**
 * Value object representation of a Book
 */
case class BookVO(id:Int, title:String, author:String, tags:List[String], cost:Double, 
  inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false) extends ValueObject[BookVO]{
  def assignId(id:Int) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the Book entity where the vocab is defined
 */
private [bookstore] object Book{
  case class AddTag(tag:String)
  case class RemoveTag(tag:String)
  case class AddInventory(amount:Int)

  def props(id:Int) = Props(classOf[Book], id)
}

/**
 * 
 */
private[inventory] class Book(idInput:Int) extends EntityActor[BookVO](idInput){
  import Book._
  import concurrent.duration._
  import context.dispatcher
  import akka.pattern.pipe
  import EntityActor._
  
  val repo = new BookRepository
  val errorMapper:ErrorMapper = PartialFunction.empty
  
  def initializedHandling:StateFunction = {
    case Event(AddTag(tag), InitializedData(vo)) =>
      requestVoForSender
      if (vo.tags.contains(tag)){
        log.info("Not adding tag {} to book {}, tag already exists", tag, vo.id)
        stay
      }
      else
        persist(vo, repo.tagBook(vo.id, tag), _ => vo.copy(tags =  vo.tags :+ tag))      
            
    case Event(RemoveTag(tag), InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.untagBook(vo.id, tag), _ => vo.copy(tags = vo.tags.filterNot( _ == tag)))      
      
    case Event(AddInventory(amount:Int), InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.addInventoryToBook(vo.id, amount), _ => vo.copy(inventoryAmount = vo.inventoryAmount + amount))      
  }
}