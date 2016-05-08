package code

import akka.actor._

object Adder{
  case object Add
  def props = Props[Adder]
}

class Adder extends Actor{
  import Adder._
  
  var amount = 0
  def receive = {
    case Add => 
      amount += 1
  }
}