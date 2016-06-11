package com.packt.masteringakka.bookstore.user

import java.util.Date
import com.packt.masteringakka.bookstore.common.ReadModelObject
import com.packt.masteringakka.bookstore.common.ViewBuilder
import akka.actor.Props


trait BookstoreUserReadModel{
  def indexRoot = "user"
  def entityType = BookstoreUser.EntityType
}

object BookstoreUserViewBuilder{
  val Name = "user-view-builder"
  case class BookstoreUserRM(email:String, firstName:String, lastName:String, 
    createTs:Date, deleted:Boolean = false) extends ReadModelObject {
    def id = email
  }
  def props = Props[BookstoreUserViewBuilder]
}

class BookstoreUserViewBuilder extends ViewBuilder[BookstoreUserViewBuilder.BookstoreUserRM] with BookstoreUserReadModel{
  import BookstoreUser.Event._
  import ViewBuilder._
  import BookstoreUserViewBuilder._
  
  def projectionId = Name
  def actionFor(id:String, offset:Long, event:Any) = event match {
    case UserCreated(user) =>
      val rm = BookstoreUserRM(user.email, user.firstName, user.lastName, user.createTs, user.deleted)
      InsertAction(id, rm)
      
    case PersonalInfoUpdated(first, last) =>
      UpdateAction(id, "firstName = fn; ctx._source.lastName = ln", Map("fn" -> first, "ln" -> last))
      
    case UserDeleted(email) =>
      UpdateAction(id, "deleted = true", Map())
  }
}