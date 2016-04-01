package com.packt.masteringakka.bookstore.book

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
import unfiltered.response.Pass

@Sharable
class BookEndpoint(bookManager:ActorRef)(implicit val system:ActorSystem, ec:ExecutionContext) extends BookstorePlan{
  import akka.pattern.ask
  
  object TagParam extends Params.Extract("tag", {values => 
    val filtered = values.filter(_.nonEmpty)
    if (filtered.isEmpty) None else Some(filtered) 
  })
  
  object AuthorParam extends Params.Extract("author", Params.first ~> Params.nonempty)

  def intent = {
    case req @ GET(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: Nil))) =>
      val f = (bookManager ? FindBook(bookId))
      respond(f, req)
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      val f = (bookManager ? FindBooksByTags(tags))
      respond(f, req) 
      
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = (bookManager ? FindBooksByAuthor(author))
      respond(f, req)       
      
    case req @ POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = extractBody[CreateBook](Body.string(req))
      val f = (bookManager ? createBook)
      respond(f, req)
      
    case req @ Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "tag" :: tag :: Nil)) =>
      req match{
        case PUT(_) => 
          respond((bookManager ? AddTagToBook(bookId, tag)), req)
        case DELETE(_) => 
          respond((bookManager ? RemoveTagFromBook(bookId, tag)), req)
        case other => 
          req.respond(Pass)
      }
      
    case req @ PUT(Path(Seg("api" :: "book" :: IntPathElement(bookId) :: "inventory" :: IntPathElement(amount) :: Nil))) =>
      val f = (bookManager ? AddInventoryToBook(bookId, amount))
      respond(f, req)
  }
}