package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow
import akka.stream.Fusing

object StreamFusion extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()  
  
  val flow = 
    Flow[Int].
      map(_*3).
      filter(_ % 2 == 0)
  val fused = Fusing.aggressive(flow)
  Source(List(1,2,3,4,5)).
    via(fused).
    runForeach(println)
}