package com.packt.masteringakka.bookstore.order

import com.packt.masteringakka.bookstore.common.EntityRepository
import scala.concurrent.Future
import concurrent.ExecutionContext
import slick.dbio.DBIOAction
import slick.jdbc.GetResult

object SalesOrderRepository{
  val BaseSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"
  implicit val GetOrder = GetResult{r => SalesOrder(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp)}
  implicit val GetLineItem = GetResult{r => SalesOrderLineItem(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp)}  
  class InventoryNotAvailaleException extends Exception
}

class SalesOrderRepository(implicit ex:ExecutionContext) extends EntityRepository[SalesOrderVO]{
  import slick.driver.PostgresDriver.api._
  import RepoHelpers._
  import SalesOrderRepository._  
  
  def loadEntity(id:Int) = Future.successful(None)
  def persistEntity(order:SalesOrderVO) = {
    val insertHeader = sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """
      
    val getId = lastIdSelect("salesorderheader")
    
    def insertLineItems(orderId:Int) = order.lineItems.map{ item =>
      val insert = 
        sqlu"""
          insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
          values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
        """
      
      //Using optimistic currency control on the update via the where clause
      val decrementInv = 
        sqlu"""
          update Book set inventoryAmount = inventoryAmount - ${item.quantity} where id = ${item.bookId} and inventoryAmount >= ${item.quantity}        
        """
          
      insert.
        andThen(decrementInv).
        filter(_ == 1)
    }
    
    
    val txn = 
      for{
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield{
        id.head
      }
      
    db.
      run(txn.transactionally).
      recoverWith{
        case ex:NoSuchElementException => Future.failed(new InventoryNotAvailaleException)
      }    
  }
  
  //Not currently handled...
  def deleteEntity(id:Int) = Future.successful(1)
}