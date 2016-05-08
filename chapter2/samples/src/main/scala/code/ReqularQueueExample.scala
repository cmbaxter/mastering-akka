package code

trait Queue{
  def enqueue(item:Int):Unit
  def dequeue():Int
}

class UnsafeQueue extends Queue{
  var items:List[Int] = Nil
  
  def enqueue(item:Int) =
    items = items :+ item
  
  def dequeue():Int = {
    if (items.isEmpty) 0
    else{
      val item = items.head
      items = items.drop(1)
      item
    }
  }
}

class SafeQueue extends Queue{
  var items:List[Int] = Nil
  def enqueue(item:Int) = synchronized{
    items = items :+ item
    notifyAll()
  }
  def dequeue():Int = synchronized{
    while(items.isEmpty){
      wait()
    }
    val item = items.head
    items = items.drop(1)
    item
  }
}

class Producer(queue:Queue)
  extends Thread{
  
  override def run = {
    for(i <- 1 to 1000){
      queue.enqueue(1)
    }
  }
}

class Consumer(queue:Queue)
  extends Thread{
  
  override def run = {
    for(i <- 1 to 1000){
      queue.dequeue
    }
  }
}

object RegularQueueExample extends App{
  val queueType = args.headOption.getOrElse("unsafe")
  println(s"using queue type: $queueType")
  val q = 
    if (queueType == "safe") new SafeQueue
    else new UnsafeQueue

  for(i <- 1 to 10){
    new Producer(q).start
    new Consumer(q).start
  }
}