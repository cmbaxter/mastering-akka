package com.packt.masteringakka.bookstore.inventory

import com.packt.masteringakka.bookstore.common.BookStoreActor
import akka.actor.Props
import akka.actor.Stash
import java.util.Date
import akka.actor.ReceiveTimeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.Status
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.EmptyResult
import com.packt.masteringakka.bookstore.common.ErrorMessage
import akka.actor.FSM
import com.packt.masteringakka.bookstore.common.ServiceResult

/**
 * Value object representation of a Book
 */
case class BookVO(id:Int, title:String, author:String, tags:List[String], cost:Double, 
  inventoryAmount:Int, createTs:Date, modifyTs:Date, deleted:Boolean = false)

/**
 * Companion to the Book entity where the vocab is defined
 */
private [bookstore] object Book{
  case object GetValueObject
  case object Initialize
  case class Initialized(bookVO:Option[BookVO])
  case class AddTag(tag:String)
  case class RemoveTag(tag:String)
  case class AddInventory(amount:Int)
  case object Delete
  
  sealed trait State
  case object Initializing extends State
  case object Initialized extends State
  case object Missing extends State
  case object Creating extends State
  case object Persisting extends State
  case object FailedToLoad extends State
  
  sealed trait Data
  case object NoData extends Data
  case class InitializingData(id:Int) extends Data
  case class MissingData(id:Int, deleted:Option[BookVO] = None) extends Data
  case class InitializedData(vo:BookVO) extends Data
  case class PersistingData(vo:BookVO, f:Int => BookVO, newInstance:Boolean = false) extends Data
  
  def props(id:Int) = Props(classOf[Book], id)
  
  object NonStateTimeout{
    def unapply(any:Any) = any match{
      case FSM.StateTimeout => None
      case _ => Some(any)
    }
  }
}

/**
 * 
 */
private[inventory] class Book(idInput:Int) extends BookStoreActor with Stash with FSM[Book.State, Book.Data]{
  import Book._
  import concurrent.duration._
  import context.dispatcher
  import akka.pattern.pipe
  
  val repo = new BookRepository
  
  if (idInput == 0) {
    startWith(Creating, NoData)
  }
  else {
    startWith(Initializing, InitializingData(idInput))
    self ! Initialize    
  }
  
  when(Initializing){
    case Event(Initialize, data:InitializingData) =>
      log.info("Initializing state data for Book {}", data.id )
      repo.loadBook(data.id).map(vo => Initialized(vo)) pipeTo self      
      stay
      
    case Event(Initialized(Some(vo)), _) =>
      unstashAll
      goto(Initialized) using InitializedData(vo)
      
    case Event(Initialized(None), data:InitializingData) =>
      log.error("No entity of type {} for id {}", getClass.getSimpleName, idInput)
      unstashAll
      goto(Missing) using MissingData(data.id)     
      
    case Event(Status.Failure(ex), data:InitializingData) =>
      log.error(ex, "Error initializing Book {}, stopping", data.id)
      goto(FailedToLoad) using data 
      
    case Event(NonStateTimeout(other), _) =>    
      stash
      stay
  }
  
  when(Missing, 1 second){
    case Event(GetValueObject, data:MissingData) =>
      val result = data.deleted.map(FullResult.apply).getOrElse(EmptyResult)
      sender ! result
      stay
      
    case Event(NonStateTimeout(other), _) =>
      sender ! Failure(FailureType.Validation, ErrorMessage.InvalidEntityId )
      stay
  }
  
  when(Creating){
    case Event(vo:BookVO, _) =>
      requestVoForSender
      persist(vo, repo.persistBook(vo), id => vo.copy(id = id), true)          
  }
  
  when(Initialized, 60 second){
    /*case Event(newVo:BookVO, data:InitializedData) =>
      transition(initialized(newVo))*/
    
    case Event(GetValueObject, InitializedData(vo)) =>
      sender ! FullResult(vo)
      stay
      
    case Event(AddTag(tag), InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.tagBook(vo.id, tag), _ => vo.copy(tags =  vo.tags :+ tag))      
            
    case Event(RemoveTag(tag), InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.untagBook(vo.id, tag), _ => vo.copy(tags = vo.tags.filterNot( _ == tag)))      
      
    case Event(AddInventory(amount:Int), InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.addInventoryToBook(vo.id, amount), _ => vo.copy(inventoryAmount = vo.inventoryAmount + amount))      
      
    case Event(Delete, InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.deleteBook(idInput), _ => vo.copy(deleted = true))       
  }
  
  when(Persisting){
    case Event(i:Int, PersistingData(vo, f, newInstance)) =>  
      val newVo = f(i)
      unstashAll
      
      if (newVo.deleted){
        goto(Missing) using MissingData(newVo.id, Some(newVo))
      }
      else{
        if (newInstance) setStateTimeout(Initialized, Some(1 second))
        goto(Initialized) using InitializedData(newVo)
      }      
            
    case Event(Status.Failure(ex), data:PersistingData) =>
      log.error(ex, "Failed on an update operation to book {}", data.vo.id)
      goto(Initialized) using InitializedData(data.vo) forMax(1 second)
      
    case Event(NonStateTimeout(other), _) => 
      stash
      stay
  }
  
  when(FailedToLoad, 1 second){
    case Event(NonStateTimeout(other), _) =>
      sender ! Failure(FailureType.Service , ServiceResult.UnexpectedFailure)
      stay    
  }
  
  whenUnhandled{
    case Event(StateTimeout, _) =>
      log.info("{} entity {} has reached max idle time, stopping instance", getClass.getSimpleName, self.path.name)      
      stop
  }

  
  def requestVoForSender = self.tell(GetValueObject, sender())
  
  def persist(vo:BookVO, f: => Future[Int], voF:Int => BookVO, newInstance:Boolean = false) = {
    val daoResult = f
    daoResult pipeTo self
    goto(Persisting) using PersistingData(vo, voF, newInstance)
          
  }
}