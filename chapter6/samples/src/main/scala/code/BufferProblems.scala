package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Fusing
import akka.stream.Attributes
import scala.concurrent.duration._
import akka.stream.ThrottleMode
import akka.stream.scaladsl._
import akka.stream.ClosedShape

object BufferProblems extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer() 
  
  case class Tick() 
  val fastSource = Source.tick(1 second, 1 second, Tick())
  val slowSource = Source.tick(3 second, 3 second, Tick())

  val asyncZip = 
    Flow[Int].
      zip(slowSource).async
   
  fastSource.
    conflateWithSeed(seed = (_) => 1)((count, _) => count + 1).   
    via(asyncZip).
    runForeach{case (i,t) => println(i)}
}