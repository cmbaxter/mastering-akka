package com.packt.masteringakka.bookstore.domain.user

import java.util.Date

//Persistent entities
case class BookstoreUser(id:Int, firstName:String, lastName:String, email:String, createTs:Date, modifyTs:Date)

//Lookup Operations
case class FindUserById(id:Int)

//Modify operations
case class UserInput(firstName:String, lastName:String, email:String)
case class CreateUser(input:UserInput)
case class UpdateUserInfo(id:Int, input:UserInput)