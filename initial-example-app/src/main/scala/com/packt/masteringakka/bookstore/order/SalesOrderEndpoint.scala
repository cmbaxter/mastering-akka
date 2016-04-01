package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.BookstorePlan
import unfiltered.response.ResponseString
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import unfiltered.response.InternalServerError
import akka.actor.ActorSystem
import akka.actor.ActorRef
import unfiltered.request._
import unfiltered.request.Seg
import io.netty.channel.ChannelHandler.Sharable

@Sharable
class SalesOrderEndpoint(salesHandler:ActorRef)(implicit val system:ActorSystem, ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask

  def intent = {
    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = extractBody[CreateOrder](Body.string(req))
      val f = (salesHandler ? createReq)
      respond(f, req)          
  }
}