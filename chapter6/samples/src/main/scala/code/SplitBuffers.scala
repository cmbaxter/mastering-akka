package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow
import akka.stream.Fusing
import akka.stream.Attributes

object SplitBuffers extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()  
  
  val separateMapStage = 
    Flow[Int].
      map(_*2).
      async.
      withAttributes(Attributes.inputBuffer(initial = 1, max = 1))
   
  val otherMapStage = 
    Flow[Int].
      map(_/2).
      async
      
  val totalFlow = separateMapStage.via(otherMapStage )
}