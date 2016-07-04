package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode

object ExplicitBuffer extends App{
  import concurrent.duration._
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer() 
  
   
  Source(1 to 100000000).
    map{x => println(s"passing $x");x}.
    buffer(5, OverflowStrategy.backpressure).
    throttle(1, 1 second, 1, ThrottleMode.shaping).
    runForeach(println)  
}