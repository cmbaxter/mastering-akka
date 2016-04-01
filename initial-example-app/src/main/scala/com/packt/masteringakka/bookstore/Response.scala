package com.packt.masteringakka.bookstore

case class ApiResonseMeta(statusCode:Int, error:Option[ErrorMessage] = None)
case class ApiResponse[T <: AnyRef](meta:ApiResonseMeta, response:Option[T] = None)

sealed abstract class ServiceResult[+A] {
  def isEmpty:Boolean
  def isValid:Boolean
  def getOrElse[B >: A](default: => B): B = default  
  def map[B](f: A => B): ServiceResult[B] = EmptyResult   
  def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = EmptyResult 
  def filter(p: A => Boolean): ServiceResult[A] = this 
  def toOption = this match{
    case FullResult(a) => Some(a)
    case _ => None
  }
}

object ServiceResult{
  val UnexpectedFailure = ErrorMessage("common.unexpect", Some("An unexpected exception has occurred"))
  
  implicit def sr2Iterable[A](sr:ServiceResult[A]):Iterable[A] = sr match{
    case FullResult(a) => List(a)
    case _ => List()
  }  
  
  def fromOption[A](opt:Option[A]):ServiceResult[A] = opt match {
    case None => EmptyResult
    case Some(value) => FullResult(value)
  }

  def fromTry[A](tr:util.Try[A]):ServiceResult[A] = tr match{
    case util.Success(value) => FullResult(value)
    case util.Failure(ex:Throwable) => Failure(ServiceFailure, UnexpectedFailure, Some(ex))
  }
}

sealed abstract class Empty extends ServiceResult[Nothing]{
  def isValid:Boolean = false
  def isEmpty:Boolean = true
}
case object EmptyResult extends Empty


final case class FullResult[+A](value:A) extends ServiceResult[A]{
  def isValid:Boolean = true
  def isEmpty:Boolean = false
  override def getOrElse[B >: A](default: => B): B = value
  override def map[B](f: A => B): ServiceResult[B] = FullResult(f(value)) 
  override def filter(p: A => Boolean): ServiceResult[A] = if (p(value)) this else EmptyResult 
  override def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = f(value)   
}

sealed trait FailureType
final case object ValidationFailure extends FailureType{ override def toString = "validation"}
final case object ServiceFailure extends FailureType{ override def toString = "service"}
case class ErrorMessage(code:String, shortText:Option[String] = None, params:Option[Map[String,String]] = None)
sealed case class Failure(failType:FailureType, message:ErrorMessage, exception:Option[Throwable] = None) extends Empty{
  type A = Nothing
  override def map[B](f: A => B): ServiceResult[B] = this  
  override def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = this   
}
