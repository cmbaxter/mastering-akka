package com.packt.masteringakka.bookstore.user

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import com.packt.masteringakka.bookstore.BookstorePlan
import akka.actor.ActorRef
import unfiltered.request._
import io.netty.channel.ChannelHandler.Sharable

@Sharable
class UserEndpoint(userManager:ActorRef)(implicit val system:ActorSystem, ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  
  def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (userManager ? FindUserById(userId))
      respond(f, req)
    
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = extractBody[UserInput](Body.string(req))
      val f = (userManager ? CreateUser(input))
      respond(f, req)
      
    case req @ PUT(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val input = extractBody[UserInput](Body.string(req))
      val f = (userManager ? UpdateUserInfo(userId, input))
      respond(f, req)      
  }
}