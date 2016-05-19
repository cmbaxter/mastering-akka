package com.packt.masteringakka.bookstore.order

import java.util.Date
import com.packt.masteringakka.bookstore.common.ValueObject
import com.packt.masteringakka.bookstore.common.EntityActor
import akka.actor.ActorRef
import com.packt.masteringakka.bookstore.user.BookstoreUserVO
import com.packt.masteringakka.bookstore.inventory.BookVO
import akka.actor.Identify
import concurrent.duration._
import akka.actor.ActorIdentity
import akka.actor.FSM
import com.packt.masteringakka.bookstore.user.CustomerRelationsManager
import com.packt.masteringakka.bookstore.inventory.InventoryClerk
import com.packt.masteringakka.bookstore.common.FullResult
import com.packt.masteringakka.bookstore.common.Failure
import com.packt.masteringakka.bookstore.common.FailureType
import com.packt.masteringakka.bookstore.common.ServiceResult
import com.packt.masteringakka.bookstore.common.ErrorMessage
import com.packt.masteringakka.bookstore.common.EmptyResult
import com.packt.masteringakka.bookstore.credit.ChargeCreditCard
import com.packt.masteringakka.bookstore.credit.CreditCardTransaction
import com.packt.masteringakka.bookstore.credit.CreditTransactionStatus
import com.packt.masteringakka.bookstore.credit.CreditCardTransactionHandler
import akka.actor.Props

case class SalesOrderVO(id:Int, userId:Int, creditTxnId:Int, 
    status:SalesOrderStatus.Value, totalCost:Double, 
    lineItems:List[SalesOrderLineItem], createTs:Date, modifyTs:Date, deleted:Boolean = false) extends ValueObject[SalesOrderVO]{
    def assignId(id:Int) = this.copy(id = id)
    def markDeleted = this.copy(deleted = true)
}

/**
 * Companion to the SalesOrderAggregate
 */
object SalesOrderAggregate{
  import EntityActor._
  
  def props(id:Int) = Props(classOf[SalesOrderAggregate], id)
  
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  case object WritingEntity extends State  
  
  case class Inputs(originator:ActorRef, request:CreateOrder)
  trait InputsData extends Data{
    def inputs:Inputs
    def originator = inputs.originator 
  }  
  case class UnresolvedDependencies(inputs:Inputs, userMgr:Option[ActorRef] = None, 
    bookMgr:Option[ActorRef] = None, creditHandler:Option[ActorRef] = None) extends InputsData

  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[Int], 
    user:Option[BookstoreUserVO], books:Map[Int, BookVO], userMgr:ActorRef, 
    bookMgr:ActorRef, creditHandler:ActorRef) extends InputsData

  case class LookedUpData(inputs:Inputs, user:BookstoreUserVO, 
    items:List[SalesOrderLineItem], total:Double) extends InputsData
  
  object ResolutionIdent extends Enumeration{
    val Book, User, Credit = Value
  }  
    
  val ResolveTimeout = 5 seconds
  
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))  
}

/**
 * Aggregate root for the SalesOrder and SalesOrderLineItem entities
 */
class SalesOrderAggregate(idInput:Int) extends EntityActor[SalesOrderVO](idInput){
  import SalesOrderAggregate._
  import SalesOrderRepository._
  import EntityActor._
  import context.dispatcher
  
  val repo = new SalesOrderRepository
  val errorMapper:ErrorMapper = {
    case ex:InventoryNotAvailaleException =>
      Failure(FailureType.Validation, InventoryNotAvailError )      
  }
  
  override def customCreateHandling:StateFunction = {
    case Event(req:CreateOrder, _) =>
      lookup(InventoryClerk.Name  ) ! Identify(ResolutionIdent.Book)
      lookup(CustomerRelationsManager.Name) ! Identify(ResolutionIdent.User )
      lookup(CreditCardTransactionHandler.Name ) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), req))      
  }
  
  when(ResolvingDependencies, ResolveTimeout )(transform {
    case Event(ActorIdentity(identifier:ResolutionIdent.Value, actor @ Some(ref)), 
      data:UnresolvedDependencies) =>
        
      log.info("Resolved dependency {}, {}", identifier, ref)
      val newData = identifier match{
        case ResolutionIdent.Book => data.copy(bookMgr = actor)
        case ResolutionIdent.User => data.copy(userMgr = actor)
        case ResolutionIdent.Credit => data.copy(creditHandler = actor)
      }
      stay using newData
  } using{
    case FSM.State(state, UnresolvedDependencies(inputs, Some(user), 
      Some(book), Some(credit)), _, _, _) =>
        
      log.info("Resolved all dependencies, looking up entities")
      user ! CustomerRelationsManager.FindUserById(inputs.request.userId)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => book ! InventoryClerk.FindBook(id))
      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, book, user, credit)
  })
  
  when(LookingUpEntities, 10 seconds)(transform {
    case Event(FullResult(b:BookVO), data:ResolvedDependencies) =>      
      log.info("Looked up book: {}", b) 
      
      //Make sure inventory is available
      val lineItemForBook = data.inputs.request.lineItems.find(_.bookId == b.id)
      lineItemForBook match{
        case None =>
          log.error("Got back a book for which we don't have a line item")
          data.originator ! unexpectedFail
          stop
        
        case Some(item) if item.quantity > b.inventoryAmount  =>
          log.error("Inventory not available for book with id {}", b.id)
          data.originator ! Failure(FailureType.Validation, InventoryNotAvailError )
          stop
          
        case _ =>
          stay using data.copy(books = data.books ++ Map(b.id -> b))    
      }      
      
    case Event(FullResult(u:BookstoreUserVO), data:ResolvedDependencies) =>      
      log.info("Found user: {}", u)      
      stay using data.copy(user = Some(u)) 
      
    case Event(EmptyResult, data:ResolvedDependencies) => 
      val (etype, error) = 
        if (sender().path.name == InventoryClerk.Name ) ("book", InvalidBookIdError) 
        else ("user", InvalidUserIdError )
      log.info("Unexpected result type of EmptyResult received looking up a {} entity", etype)
      data.originator ! Failure(FailureType.Validation, error)
      stop
      
  } using{
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u), 
      bookMap, userMgr, bookMgr, creditMgr), _, _, _) 
      if bookMap.keySet == expectedBooks =>            
      
      log.info("Successfully looked up all entities and inventory is available, charging credit card")
      val lineItems = inputs.request.lineItems.
        flatMap{item => 
          bookMap.
            get(item.bookId).
            map(b => SalesOrderLineItem(0, 0, b.id, item.quantity, item.quantity * b.cost, newDate, newDate))
        }
         
      val total = lineItems.map(_.cost).sum
      creditMgr ! ChargeCreditCard(inputs.request.cardInfo, total)      
      goto(ChargingCard) using LookedUpData(inputs, u, lineItems, total)
  }) 
  
  when(ChargingCard, 10 seconds){
    case Event(FullResult(txn:CreditCardTransaction), data:LookedUpData) if txn.status == CreditTransactionStatus.Approved  =>           
      val order = SalesOrderVO(0, data.user.id, txn.id, SalesOrderStatus.InProgress, data.total, data.items, newDate, newDate)
      requestVoForSender(data.inputs.originator)
      persist(order, repo.persistEntity(order), id => order.copy(id = id), true)
      
    case Event(FullResult(txn:CreditCardTransaction), data:LookedUpData) =>
      log.info("Credit was rejected for request: {}", data.inputs.request)
      data.originator ! Failure(FailureType.Validation, CreditRejectedError )
      stop      
  }  
  
  def initializedHandling:StateFunction = PartialFunction.empty
  
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure )
  def newDate = new Date
  def lookup(name:String) = context.actorSelection(s"/user/$name")  
}