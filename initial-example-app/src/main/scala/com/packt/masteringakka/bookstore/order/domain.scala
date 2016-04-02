package com.packt.masteringakka.bookstore.order

import java.util.Date
import com.packt.masteringakka.bookstore.credit.CreditCardInfo

//Persistent entities
object SalesOrderStatus extends Enumeration{
  val InProgress, Shipped, Cancelled = Value
}
case class SalesOrder(id:Int, userId:Int, creditTxnId:Int, status:SalesOrderStatus.Value, totalCost:Double, lineItems:List[SalesOrderLineItem], createTs:Date, modifyTs:Date)
case class SalesOrderLineItem(id:Int, orderId:Int, bookId:Int, quantity:Int, cost:Double, createTs:Date,  modifyTs:Date)

//Create/Modify requests
case class LineItemRequest(bookId:Int, quantity:Int)
case class CreateOrder(userId:Int, lineItems:List[LineItemRequest], cardInfo:CreditCardInfo)