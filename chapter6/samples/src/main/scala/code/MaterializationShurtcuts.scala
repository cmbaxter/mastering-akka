package code

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow

object MaterializationShurtcuts extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()
  
  val source = Source(List(1,2,3,4,5))
  val sink = Sink.fold[Int,Int](0)(_+_)
  val multiplier = Flow[Int].map(_*2)
  
  //Hook source and sink into flow and run
  multiplier.runWith(source, sink)
  
  //Hook flow to sink and then run with a source
  multiplier.to(sink).runWith(source)
  
  //Connect a flow with a source and then run with a fold
  source.via(multiplier).runFold(0)(_+_)
}