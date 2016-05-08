package code

import akka.actor._
import akka.routing.RoundRobinPool
import scala.io.Source
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import akka.pattern.ask
import concurrent.duration._
import akka.routing.RoundRobinPool

object WorkMaster{
  case object StartProcessing
  case object DoWorkerWork
  case class IterationCount(count:Long)
  def props(workerCount:Int) = Props(classOf[WordCountMaster], workerCount)
}

class WordCountMaster(workerCount:Int) extends Actor{
  import WorkMaster._
  
  val workers = context.actorOf(
    ParallelismWorker.props.withRouter(
        RoundRobinPool(workerCount)), "workerRouter")  
  def receive = waitingForRequest
    
  def waitingForRequest:Receive = {
    case StartProcessing =>                 
      val requestCount = 50000
      for(i <- 1 to requestCount){
        workers ! DoWorkerWork
      }
      context.become(collectingResults(requestCount, sender()))
  }  
  
  def collectingResults(remaining:Int, caller:ActorRef, iterations:Long = 0):Receive = {
    case IterationCount(count) =>
      val newRemaining = remaining - 1
      val newIterations = count + iterations
      if (newRemaining == 0){
        caller ! IterationCount(newIterations)
        context.stop(self)
        context.system.terminate
        
      }
      else{
        context.become(collectingResults(newRemaining, caller, newIterations))
      }
  }
}

object ParallelismWorker{  
  def props() = Props[ParallelismWorker]
}

class ParallelismWorker extends Actor {
  import WorkMaster._
  
  def receive = {
    case DoWorkerWork =>
      var totalIterations = 0L
      var count = 10000000
      while(count > 0){
        totalIterations += 1
        count -= 1
      }            

      sender() ! IterationCount(totalIterations)
  }
}

object ParallelismExample extends App{
  implicit val timeout = Timeout(60 seconds)  
  val workerCount = args.headOption.getOrElse("8").toInt
  println(s"Using $workerCount worker instances")

  val system = ActorSystem("wordcount")
  val master = system.actorOf(
    WorkMaster.props(workerCount), "master")
  val fut = 
    (master ? WorkMaster.StartProcessing).
      mapTo[WorkMaster.IterationCount]
  val start = System.currentTimeMillis()
  
  import system.dispatcher
  fut onComplete{
    case util.Success(iterations) =>
      val time = System.currentTimeMillis() - start      
      println(s"total time was: $time ms")
      println(s"total iterations was: ${iterations.count}")
      
    case util.Failure(ex) =>
      ex.printStackTrace()
      system.terminate
  }
}