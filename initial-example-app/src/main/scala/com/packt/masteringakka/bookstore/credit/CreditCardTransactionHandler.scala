package com.packt.masteringakka.bookstore.credit

import akka.actor._
import com.packt.masteringakka.bookstore.BookstoreDao
import scala.concurrent.ExecutionContext
import slick.driver.PostgresDriver.api._
import slick.dbio.DBIOAction
import java.util.Date
import com.packt.masteringakka.bookstore.BookStoreActor

object CreditCardTransactionHandler{
  val Name = "credit-handler"
  def props = Props[CreditCardTransactionHandler]
}

class CreditCardTransactionHandler extends BookStoreActor{
  import akka.pattern.pipe
  import context.dispatcher  
  
  val dao = new CreditCardTransactionHandlerDao

  def receive = {
    case ChargeCreditCard(info, amount) =>
      //TODO: Add in logic to execute the remote call
      val txn = CreditCardTransaction(0, info, amount, CreditTransactionStatus.Approved, Some("foobar"), new Date, new Date)
      val result = dao.createCreditTransaction(txn)
      pipeResponse(result)
  }
}

object CreditCardTransactionHandlerDao{
  
}

class CreditCardTransactionHandlerDao(implicit ec:ExecutionContext) extends BookstoreDao{
  import DaoHelpers._
  
  def createCreditTransaction(txn:CreditCardTransaction) = {
    val info = txn.cardInfo 
    val insert = sqlu"""
      insert into CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs) 
      values (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${txn.amount}, ${txn.status.toString}, ${txn.confirmationCode}, ${txn.createTs.toSqlDate}, ${txn.modifyTs.toSqlDate})
    """
    val getId = lastIdSelect("creditcardtransaction")
    db.run(insert.andThen(getId).withPinnedSession).map(v => txn.copy(id = v.headOption.getOrElse(0)))
  }
  
}
