package code

import akka.stream.scaladsl._
import akka.NotUsed
import akka.stream.ClosedShape
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import java.util.Date
import scala.concurrent.Future
import akka.Done

object EventGraphExample extends App{
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()
  
  case class WeatherData(temp:Int, rain:Boolean)
  case class ImageInfo(tags:List[String], colors:List[String])
  case class Event(eventType:String, date:Date, imageUrl:String, 
     weather:Option[WeatherData], imageInfo:Option[ImageInfo])
     
  def s3EventSource:Source[Event, NotUsed] = null
  def fetchWeatherInfo(date:Date):Future[WeatherData] = null
  def fetchImageInfo(imageUrl:String):Future[ImageInfo] = null
  def redshiftSink:Sink[Event, Future[Done]]= null     

  val g = RunnableGraph.fromGraph(GraphDSL.create() { 
    implicit builder: GraphDSL.Builder[NotUsed] =>
    
    import GraphDSL.Implicits._
    val eventsSource = s3EventSource
    val eventsSink = redshiftSink
    val weather = 
      Flow[Event].mapAsync(4)(e => fetchWeatherInfo(e.date))
    val imageInfo = 
      Flow[Event].mapAsync(4)(e => fetchImageInfo(e.imageUrl))      
        
    val bcast = builder.add(Broadcast[Event](3))
    val zip = builder.add(ZipWith[Event,WeatherData,ImageInfo,Event]{(e, w, i) => 
      e.copy(weather = Some(w), imageInfo = Some(i))      
    })
    
    eventsSource ~> bcast ~> zip.in0 
                    bcast ~> weather ~> zip.in1 
                    bcast ~> imageInfo ~> zip.in2 
    zip.out ~> eventsSink
    ClosedShape
  })
  g.run
  
}