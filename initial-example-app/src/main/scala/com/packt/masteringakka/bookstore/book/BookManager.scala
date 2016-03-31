package com.packt.masteringakka.bookstore.book

import akka.actor.Actor
import akka.actor.Props
import slick.driver.PostgresDriver.api._
import com.packt.masteringakka.bookstore.PostgresDB
import slick.jdbc.GetResult
import java.sql.Date
import slick.dbio.DBIOAction
import com.packt.masteringakka.bookstore.BookstoreDao
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object BookManager{
  def props = Props[BookManager]
}

class BookManager extends Actor{  
  import akka.pattern.pipe
  import context.dispatcher
  
  val dao = new BookManagerDao

  def receive = {
    case FindBook(id) => 
      val result = dao.findBookById(id)
      result pipeTo sender()
      
    case FindBooksForIds(ids) =>
      val result = lookupBooksByIds(ids)
      result pipeTo sender()
      
    case FindBooksByTags(tags) => 
      val idsFut = dao.findBookIdsByTags(tags)
      val result = 
        for{
          ids <- idsFut
          books <- lookupBooksByIds(ids)
        } yield books
      result pipeTo sender()
            
    case CreateBook(book) =>
      val result = dao.createBook(book)
      result pipeTo sender()
      
    case AddTagToBook(id, tag) =>
      val result = manipulateTags(id, tag)(dao.tagBook)        
      result pipeTo sender()
        
    case RemoveTagFromBook(id, tag) =>
      val result = manipulateTags(id, tag)(dao.untagBook)        
      result pipeTo sender()        
  }
  
  def manipulateTags(id:Int, tag:String)(f:(Book,String) => Future[Book]):Future[Option[Book]] = {
    for{
      book <- dao.findBookById(id)
      tagRes <- checkExistsAndThen(book)(b => f(b, tag))
    } yield tagRes    
  }
  
  def checkExistsAndThen(book:Option[Book])(f:Book => Future[Book]):Future[Option[Book]] = {
    book.fold(Future.successful(book))(b => f(b).map(Some(_)))      
  }
    
  def lookupBooksByIds(ids:Seq[Int]) = 
    if (ids.isEmpty) Future.successful(Vector.empty)
    else dao.findBooksByIds(ids)
}

object BookManagerDao{
  implicit val GetBook = GetResult{r => Book(r.<<, r.<<, r.<<, r.nextString.split(",").filter(_.nonEmpty).toList, r.<<, r.<<)}
}

class BookManagerDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import BookManagerDao._
  
  def findBookById(id:Int) = findBooksByIds(Seq(id)).map(_.headOption)
  
  def findBooksByIds(ids:Seq[Int]) = {
    val idsParam = s"${ids.mkString(",")}"
    val booksResult = 
      db.run(sql"""
        select b.id, b.title, b.author, array_to_string(array_agg(t.tag), ',') as tags, b.cost, b.inventoryAmount 
        from Book b left join BookTag t on b.id = t.bookId where b.id in (#$idsParam) group by b.id
        """.as[Book]
      )
    booksResult    
  }
  
  def findBookIdsByTags(tags:Seq[String]) = {
    val tagsParam = tags.map(t => s"'$t'").mkString(",")      
    val idsWithAllTags = db.run(sql"select bookId, count(bookId) from BookTag where tag in (#$tagsParam) group by bookId having count(bookId) = ${tags.size}".as[(Int,Int)])    
    idsWithAllTags.map(_.map(_._1) )     
  }
  
  def createBook(book:Book) = {
    val insert = 
      sqlu"""
        insert into Book (title, author, cost, inventoryamount, createts) 
        values (${book.title}, ${book.author}, ${book.cost}, ${book.inventoryAmount}, ${new Date(System.currentTimeMillis())})
      """
    val idget = sql"select currval('book_id_seq')".as[Int]
    def tagsInserts(bookId:Int) = DBIOAction.sequence(book.tags.map(t => sqlu"insert into BookTag (bookid, tag) values ($bookId, $t)"))
      
    val txn = 
      for{
        bookRes <- insert
        id <- idget
        if id.headOption.isDefined
        _ <- tagsInserts(id.head)
      } yield{
        book.copy(id = id.head)
      }
          
    db.run(txn.transactionally)    
  }
  
  def tagBook(book:Book, tag:String) = {
    db.run(sqlu"insert into BookTag values (${book.id}, $tag)").map(_ => book.copy(tags = book.tags :+ tag))  
  }
  
  def untagBook(book:Book, tag:String) = {
    db.run(sqlu"delete from BookTag where bookId =  ${book.id} and tag = $tag").map(_ => book.copy(tags = book.tags.filterNot( _ == tag)))  
  }  
}