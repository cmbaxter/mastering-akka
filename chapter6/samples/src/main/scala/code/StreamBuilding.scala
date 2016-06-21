package code

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.NotUsed
import scala.concurrent.Future
import akka.Done
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Flow

object StreamBuilding extends App{
  val source:Source[Int, NotUsed] = Source(1 to 5)
  val sink:Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  val dataflow:RunnableGraph[NotUsed] = source.to(sink)
  
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()
  dataflow.run
  
  val sink2:Sink[Int, Future[Int]] = Sink.fold(0)(_+_)
  val dataflow2:RunnableGraph[Future[Int]] = source.toMat(sink2)(Keep.right)
  val fut:Future[Int] = dataflow2.run
  fut.onComplete(println)(system.dispatcher)
  
  val flow = Flow[Int].map(_*2).filter(_ % 2 == 0)
  val fut2 = source.via(flow).toMat(sink2)(Keep.right).run
  fut2.onComplete(println)(system.dispatcher)
}