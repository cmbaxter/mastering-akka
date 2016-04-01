package com.packt.masteringakka.bookstore.order

import akka.actor._
import com.packt.masteringakka.bookstore.user.FindUserById
import com.packt.masteringakka.bookstore.user.BookstoreUser
import com.packt.masteringakka.bookstore.book.Book
import com.packt.masteringakka.bookstore.book.FindBook
import com.packt.masteringakka.bookstore.user.BookstoreUser
import com.packt.masteringakka.bookstore.ErrorMessage
import com.packt.masteringakka.bookstore.Failure
import com.packt.masteringakka.bookstore.ValidationFailure
import com.packt.masteringakka.bookstore.{FullResult, EmptyResult}

object SalesOrderHandler{
  val Name = "sales-handler"
  def props = Props[SalesOrderHandler]
}

class SalesOrderHandler extends Actor with ActorLogging{
  def receive = {
    case req:CreateOrder =>
      log.info("Creating new sales order processor and forwarding request")
      val proc = context.actorOf(SalesOrderProcessor.props)
      proc forward req
  }
}

object SalesOrderProcessor{
  def props = Props[SalesOrderProcessor]
  
  sealed trait State
  case object Idle extends State  
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  case object WritingEntity extends State
  
  sealed trait Data
  case object Uninitialized extends Data
  case class Inputs(originator:ActorRef, request:CreateOrder)
  case class UnresolvedDependencies(inputs:Inputs, userMgr:Option[ActorRef] = None, bookMgr:Option[ActorRef] = None, creditHandler:Option[ActorRef] = None) extends Data
  case class ResolvedDependencies(inputs:Inputs, expectedBooks:Set[Int], user:Option[BookstoreUser], books:Map[Int, Book], userMgr:ActorRef, bookMgr:ActorRef, creditHandler:ActorRef) extends Data
  
  object ResolutionIdent extends Enumeration{
    val Book, User, Credit = Value
  }
  
  val BookMgrName = "book-manager"
  val UserManagerName = "user-manager"
  val CreditHandlerName = "credit-handler"
    
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
}

class SalesOrderProcessor extends FSM[SalesOrderProcessor.State, SalesOrderProcessor.Data]{
  import SalesOrderProcessor._
  import concurrent.duration._
  
  
  startWith(Idle, Uninitialized)
  
  when(Idle){
    case Event(req:CreateOrder, _) =>
      lookup(BookMgrName ) ! Identify(ResolutionIdent.Book)
      lookup(UserManagerName ) ! Identify(ResolutionIdent.User )
      lookup(CreditHandlerName ) ! Identify(ResolutionIdent.Credit)
      goto(ResolvingDependencies) using UnresolvedDependencies(Inputs(sender(), req))
  }
  
  when(ResolvingDependencies, 5 seconds)(transform {
    case Event(ActorIdentity(identifier:ResolutionIdent.Value, actor @ Some(ref)), data:UnresolvedDependencies) =>      
      log.info("Resolved dependency {}, {}", identifier, ref)
      val newData = identifier match{
        case ResolutionIdent.Book => data.copy(bookMgr = actor)
        case ResolutionIdent.User => data.copy(userMgr = actor)
        case ResolutionIdent.Credit => data.copy(creditHandler = actor)
      }
      stay using newData
  } using{
    case FSM.State(state, UnresolvedDependencies(inputs, Some(user), Some(book), Some(credit)), _, _, _) =>
      log.info("Resolved all dependencies, looking up entities")
      user ! FindUserById(inputs.request.userId)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => book ! FindBook(id))
      goto(LookingUpEntities) using ResolvedDependencies(inputs, expectedBooks, None, Map.empty, book, user, credit)
  })
  
  when(LookingUpEntities, 10 seconds)(transform {
    case Event(FullResult(b:Book), data:ResolvedDependencies) =>      
      log.info("Looked up book: {}", b)      
      stay using data.copy(books = data.books ++ Map(b.id -> b))
      
    case Event(FullResult(u:BookstoreUser), data:ResolvedDependencies) =>      
      log.info("Found user: {}", u)      
      stay using data.copy(user = Some(u)) 
      
    case Event(EmptyResult, data:ResolvedDependencies) => 
      val (etype, error) = 
        if (sender().path.name == BookMgrName) ("book", InvalidBookIdError) 
        else ("user", InvalidUserIdError )
      log.info("Unexpected result type of EmptyResult received looking up a {} entity", etype)
      data.inputs.originator ! Failure(ValidationFailure, error)
      stop
      
  } using{
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u), bookMap, userMgr, bookMgr, creditMgr), _, _, _) if bookMap.keySet == expectedBooks =>
      log.info("Successfully looked up all entities, charging credit card")
      inputs.originator ! FullResult(inputs.request)
      stop
  })
  
  def lookup(name:String) = context.actorSelection(s"/user/$name")
}