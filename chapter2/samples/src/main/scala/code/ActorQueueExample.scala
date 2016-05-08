package code

import akka.actor.Actor
import akka.actor.Stash
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import akka.actor.Terminated

object ActorQueue{
  case class Enqueue(item:Int)
  case object Dequeue
  def props = Props[ActorQueue]
}

class ActorQueue extends Actor with Stash{
  import ActorQueue._
  
  def receive = emptyReceive
  
  def emptyReceive:Receive = {
    case Enqueue(item) =>
      context.become(nonEmptyReceive(List(item)))
      unstashAll
      
    case Dequeue =>
      stash
  }
  
  def nonEmptyReceive(items:List[Int]):Receive = {
    case Enqueue(item) =>
      context.become(nonEmptyReceive(items :+ item))
      
    case Dequeue =>
      val item = items.head
      sender() ! item
      
      val newReceive = items.tail match{
        case Nil =>
          println("Switching back to empty receive")
          emptyReceive
        case nonNil => nonEmptyReceive(nonNil)
      }
      context.become(newReceive)
  }
}

object ProducerActor{
  def props(queue:ActorRef) = Props(classOf[ProducerActor], queue)
}

class ProducerActor(queue:ActorRef) extends Actor{
  def receive = {
    case "start" =>
      for(i <- 1 to 1000) queue ! ActorQueue.Enqueue(i)
  }
}

object ConsumerActor{
  def props(queue:ActorRef) = Props(classOf[ConsumerActor], queue)
}

class ConsumerActor(queue:ActorRef) extends Actor{
 
  def receive = consumerReceive(1000)
  
  def consumerReceive(remaining:Int):Receive = {
    case "start" =>
      queue ! ActorQueue.Dequeue
      
    case i:Int =>
      val newRemaining = remaining - 1
      if (newRemaining == 0){
        println(s"Consumer ${self.path} is done consuming")
        context.stop(self)
      }
      else{
        queue ! ActorQueue.Dequeue
        context.become(consumerReceive(newRemaining))
      }
  }
}

object ActorQueueExample extends App{
  val system = ActorSystem()
  val queue = system.actorOf(ActorQueue.props)
  
  val pairs = 
    for(i <- 1 to 10) yield {
      val producer = system.actorOf(ProducerActor.props(queue))         
      val consumer = system.actorOf(ConsumerActor.props(queue))
      (consumer, producer)
    }
  
  val reaper = system.actorOf(ShutdownReaper.props)
  pairs.foreach{
    case (consumer, producer) =>
      reaper ! consumer
      consumer ! "start"
      producer ! "start"
  }
}

object ShutdownReaper{
  def props = Props[ShutdownReaper]
}

class ShutdownReaper extends Actor{
  def receive = shutdownReceive(0)
  def shutdownReceive(watching:Int):Receive = {
    case ref:ActorRef => 
      context.watch(ref)
      context.become(shutdownReceive(watching + 1))
      
    case t:Terminated if watching - 1 == 0 =>
      println("All consumers done, terminating actor system")
      context.system.terminate
    
    case t:Terminated =>
      context.become(shutdownReceive(watching - 1))
  }
}