package com.packt.masteringakka.bookstore.user

import akka.actor._
import com.packt.masteringakka.bookstore.BookstoreDao
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult
import java.util.Date
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.BookStoreActor
import com.packt.masteringakka.bookstore.ErrorMessage
import com.packt.masteringakka.bookstore.ServiceResult
import com.packt.masteringakka.bookstore.Failure
import com.packt.masteringakka.bookstore.FailureType

object UserManager{
  val Name = "user-manager"
  def props = Props[UserManager]
  class EmailNotUniqueException extends Exception
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))
}

class UserManager extends BookStoreActor{
  import akka.pattern.pipe
  import UserManager._
  import context.dispatcher
  
  val dao = new UserManagerDao
  val recoverEmailCheck:PartialFunction[Throwable, ServiceResult[_]] = {
    case ex:EmailNotUniqueException => 
      Failure(FailureType.Validation, EmailNotUniqueError )
  }
  
  def receive = {
    case FindUserById(id) =>
      log.info("Looking up user for id: {}", id)
      pipeResponse(dao.findUserById(id))
    
    case CreateUser(UserInput(first, last, email)) =>
      val result = 
        for{
          _ <- emailUnique(email)
          daoRes <- dao.createUser(BookstoreUser(0, first, last, email, new Date, new Date))
        } yield daoRes        
      pipeResponse(result.recover(recoverEmailCheck ))
      
    case upd @ UpdateUserInfo(id, input) =>
      val result = 
        for{
          _ <- emailUnique(input.email, Some(id))
          userOpt <- dao.findUserById(id)
          updated <- maybeUpdate(upd, userOpt)
        } yield updated
      pipeResponse(result.recover(recoverEmailCheck ))
      
  }
  
  def emailUnique(email:String, existingId:Option[Int] = None) = {
    dao.
      findUserByEmail(email).
      flatMap{
        case None => Future.successful(true)
        case Some(user) if Some(user.id) == existingId => Future.successful(true)
        case _ => Future.failed(new EmailNotUniqueException)
      }
  }
  
  def maybeUpdate(upd:UpdateUserInfo, userOpt:Option[BookstoreUser]) = 
    userOpt.
      map{ u =>
        val updated = u.copy(firstName = upd.input.firstName, lastName = upd.input.lastName, email = upd.input.email)
        dao.updateUserInfo(updated).map(Some.apply)
      }.
      getOrElse(Future.successful(None))
      
}

object UserManagerDao{
  val SelectFields = "select id, firstName, lastName, email, createTs, modifyTs from StoreUser " 
  implicit val GetUser = GetResult{r => BookstoreUser(r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}
}

class UserManagerDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import DaoHelpers._
  import UserManagerDao._
  
  def createUser(user:BookstoreUser) = {
    val insert = sqlu"""
      insert into StoreUser (firstName, lastName, email, createTs, modifyTs) 
      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})
    """
    val idget = lastIdSelect("storeuser")    
    db.run(insert.andThen(idget).withPinnedSession).map(id => user.copy(id = id.headOption.getOrElse(0)))
  }
  
  def findUserById(id:Int) = {
    db.
      run(sql"#$SelectFields where id = $id".as[BookstoreUser]).
      map(_.headOption)
  }
  
  def findUserByEmail(email:String) = {
    db.
      run(sql"#$SelectFields where email = $email".as[BookstoreUser]).
      map(_.headOption)
  }  
  
  def updateUserInfo(user:BookstoreUser) = {
    val update = sqlu"""
      update StoreUser set firstName = ${user.firstName}, 
      lastName = ${user.lastName}, email = ${user.email} where id = ${user.id}  
    """
    db.run(update).map(_ => user)
  }
}
