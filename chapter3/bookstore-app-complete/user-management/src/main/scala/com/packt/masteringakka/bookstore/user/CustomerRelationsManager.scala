package com.packt.masteringakka.bookstore.user

import com.packt.masteringakka.bookstore.common.BookstoreActor
import com.packt.masteringakka.bookstore.common.EntityLookupDelegate
import com.packt.masteringakka.bookstore.common.EntityActor
import akka.util.Timeout
import akka.actor.Props
import java.util.Date

object CustomerRelationsManager{
  val Name = "crm"
  case class FindUserById(id:Int)
  case class FindUserByEmail(email:String)  
  case class CreateUser(input:BookstoreUser.UserInput)
  case class UpdateUserInfo(id:Int, input:BookstoreUser.UserInput)
  case class DeleteUser(userId:Int) 
  
  def props = Props[CustomerRelationsManager]
}

/**
 * Actor class that receives requests for BookstoreUser and delegates to the appropriate entity instance
 */
class CustomerRelationsManager extends BookstoreActor with EntityLookupDelegate[BookstoreUserVO]{
  import com.packt.masteringakka.bookstore.common.EntityActor._
  import CustomerRelationsManager._
  import context.dispatcher
  
  val repo = new BookstoreUserRepository
  
  def receive = {
    case FindUserById(id) =>
      val user = lookupOrCreateChild(id)
      user.forward(GetValueObject)
      
    case FindUserByEmail(email) => 
      val result = 
        for{
          id <- repo.findUserIdByEmail(email)
          user = lookupOrCreateChild(id.getOrElse(0))
          vo <- askForVo(user)
        } yield vo
      pipeResponse(result)
        
    case CreateUser(input) =>
      val vo = BookstoreUserVO(0, input.firstName, input.lastName, input.email, new Date, new Date)
      persistOperation(vo.id, vo)
      
    case UpdateUserInfo(id, info) =>
      persistOperation(id, BookstoreUser.UpdatePersonalInfo(info))
      
    case DeleteUser(id) =>
      persistOperation(id, Delete)            
  }
  
  def entityProps(id:Int) = BookstoreUser.props(id)
}