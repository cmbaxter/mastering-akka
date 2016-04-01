package com.packt.masteringakka.bookstore.user

import akka.actor._
import com.packt.masteringakka.bookstore.BookstoreDao
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult
import java.util.Date
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.packt.masteringakka.bookstore.BookStoreActor

object UserManager{
  val Name = "user-manager"
  def props = Props[UserManager]
}

class UserManager extends BookStoreActor{
  import akka.pattern.pipe
  import context.dispatcher
  
  val dao = new UserManagerDao

  def receive = {
    case FindUserById(id) =>
      log.info("Looking up user for id: {}", id)
      pipeResponse(dao.findUserById(id))
    
    case CreateUser(UserInput(first, last, email)) =>
      //TODO: fail if email already in use (add as unique index)
      val result = dao.createUser(BookstoreUser(0, first, last, email, new Date, new Date))
      pipeResponse(result)
      
    case upd @ UpdateUserInfo(id, input) =>
      val result = 
        for{
          userOpt <- dao.findUserById(id)
          updated <- maybeUpdate(upd, userOpt)
        } yield updated
      pipeResponse(result)
      
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
      run(sql"select id, firstName, lastName, email, createTs, modifyTs from StoreUser where id = $id".as[BookstoreUser]).
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
