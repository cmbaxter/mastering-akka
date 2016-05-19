package com.packt.masteringakka.bookstore.common

import akka.actor.FSM
import akka.actor.Stash
import akka.actor.Status
import scala.concurrent.Future
import akka.actor.Props
import akka.actor.ActorRef
import akka.util.Timeout

object EntityActor{
  case object GetValueObject
  case object Initialize
  case object Delete  
  
  trait State
  case object Initializing extends State
  case object Initialized extends State
  case object Missing extends State
  case object Creating extends State
  case object Persisting extends State
  case object FailedToLoad extends State
  
  trait Data
  case object NoData extends Data
  case class InitializingData(id:Int) extends Data
  
  object NonStateTimeout{
    def unapply(any:Any) = any match{
      case FSM.StateTimeout => None
      case _ => Some(any)
    }
  }  
  
  type ErrorMapper = PartialFunction[Throwable, Failure]
}

abstract class EntityActor[VO <: ValueObject[VO]](idInput:Int) extends BookstoreActor with FSM[EntityActor.State, EntityActor.Data] with Stash{
  import EntityActor._
  import akka.pattern.pipe
  import concurrent.duration._
  import context.dispatcher
  
  case class Loaded(vo:Option[VO])
  case class MissingData(id:Int, deleted:Option[VO] = None) extends Data
  case class InitializedData(vo:VO) extends Data
  case class PersistingData(vo:VO, f:Int => VO, newInstance:Boolean = false) extends Data
  case class FinishCreate(vo:VO)
  
  val repo:EntityRepository[VO]
  val entityType = getClass.getSimpleName
  val errorMapper:ErrorMapper
  
  if (idInput == 0) {
    startWith(Creating, NoData)
  }
  else {
    startWith(Initializing, InitializingData(idInput))
    self ! Initialize    
  }
  
  when(Initializing){
    case Event(Initialize, data:InitializingData) =>
      log.info("Initializing state data for {} {}", entityType, data.id )
      repo.loadEntity(data.id).map(vo => Loaded(vo)) pipeTo self      
      stay
      
    case Event(Loaded(Some(vo)), _) =>
      unstashAll
      goto(Initialized) using InitializedData(vo)
      
    case Event(Loaded(None), data:InitializingData) =>
      log.error("No entity of type {} for id {}", entityType, idInput)
      unstashAll
      goto(Missing) using MissingData(data.id)     
      
    case Event(Status.Failure(ex), data:InitializingData) =>
      log.error(ex, "Error initializing {} {}, stopping", entityType, data.id)
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
  
  when(Creating)(customCreateHandling orElse standardCreateHandling)
  
  def customCreateHandling:StateFunction = PartialFunction.empty
  
  def standardCreateHandling:StateFunction = {
    case Event(vo:VO, _) =>
      createAndRequestVO(vo) 
    case Event(FinishCreate(vo:VO), _) =>
      createAndRequestVO(vo)
    case Event(Status.Failure(ex), _) =>
      log.error(ex, "Failed to create a new entity of type {}", entityType)
      val fail = mapError(ex)
      goto(Missing) using MissingData(0) replying(fail)
  }
  
  def createAndRequestVO(vo:VO) = {
    requestVoForSender
    persist(vo, repo.persistEntity(vo), id => vo.assignId(id), true)     
  }
  
  when(Initialized, 60 second)(standardInitializedHandling orElse initializedHandling)
     
  def standardInitializedHandling:StateFunction = {
    case Event(GetValueObject, InitializedData(vo)) =>
      sender ! FullResult(vo)
      stay  
      
    case Event(Delete, InitializedData(vo)) =>
      requestVoForSender
      persist(vo, repo.deleteEntity(vo.id), _ => vo.markDeleted)      
  }
  
  def initializedHandling:StateFunction
  
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
      log.error(ex, "Failed on an create/update operation to {} {}", entityType, data.vo.id)
      val response = mapError(ex)
      goto(Initialized) using InitializedData(data.vo) forMax(1 second) replying(response)
      
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
  
  def persist(vo:VO, f: => Future[Int], voF:Int => VO, newInstance:Boolean = false) = {
    val daoResult = f
    daoResult.to(self, sender())
    goto(Persisting) using PersistingData(vo, voF, newInstance)
          
  }  
  
  def mapError(ex:Throwable) = 
    errorMapper.lift(ex).getOrElse(Failure(FailureType.Service, ServiceResult.UnexpectedFailure ))
  
    
  def requestVoForSender:Unit = requestVoForSender(sender())
  def requestVoForSender(ref:ActorRef):Unit = self.tell(GetValueObject, ref)
}

/**
 * Trait to mix into case classes that represent lightweight value
 * value object representations of an entity
 */
trait ValueObject[VO]{
  /**
   * Assigns an id to the value object, returning a new instance
   * @param id The id to assign
   */
  def assignId(id:Int):VO
  def id:Int
  def deleted:Boolean
  def markDeleted:VO
}

trait EntityLookupDelegate[VO <: ValueObject[VO]]{ me:BookstoreActor =>
  def lookupOrCreateChild(id:Int) = {
    val name = entityActorName(id)
    context.child(name).getOrElse{
      log.info("Creating new Book actor to handle a request for id {}", id)
      if (id > 0)
        context.actorOf(entityProps(id), name)
      else
        context.actorOf(entityProps(id))
    }
  }
  
  def persistOperation(id:Int, msg:Any){
    val entity = lookupOrCreateChild(id)
    entity.forward(msg)    
  }
  
  def askForVo(bookActor:ActorRef) = {
    import akka.pattern.ask
    import concurrent.duration._
    implicit val timeout = Timeout(5 seconds)
    (bookActor ? EntityActor.GetValueObject).mapTo[ServiceResult[VO]]
  }  
  
  def entityProps(id:Int):Props
  
  def entityActorName(id:Int) = s"${getClass.getSimpleName.toLowerCase}-$id"  
 
}