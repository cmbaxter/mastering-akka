package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow
import akka.stream.Fusing

object AsyncBoundaries extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()  
  
  Source(1 to 5).
    map{x => println(s"pre-map: $x");x}.
    map(_*3).async.
    map{x => println(s"pre-filter: $x");x}.
    filter(_ % 2 == 0).    
    runForeach(x => println(s"done: $x"))
}