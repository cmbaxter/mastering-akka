package com.packt.masteringakka.bookstore.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor._
import com.datastax.driver.core._
import akka.Done
import akka.event.Logging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface into a projection's offset storage system so that it can be properly resumed
 */
abstract class ResumableProjection(identifier:String){
  def storeLatestOffset(offset:Long)(implicit ex:ExecutionContext):Future[Boolean]
  def fetchLatestOffset(implicit ex:ExecutionContext):Future[Long]
}

object ResumableProjection{
  def apply(identifier:String, system:ActorSystem) = new CassandraResumableProjection(identifier, system)
}

class CassandraResumableProjection(identifier:String, system:ActorSystem) extends ResumableProjection(identifier){
  val projectionStorage = CassandraProjectionStorage(system)
  
  def storeLatestOffset(offset:Long)(implicit ex:ExecutionContext):Future[Boolean] = null
  def fetchLatestOffset(implicit ex:ExecutionContext):Future[Long] = null
}

class CassandraProjectionStorageExt(system:ActorSystem) extends Extension{
  import system.dispatcher
  import akka.persistence.cassandra.listenableFutureToFuture
  
  val log = Logging(system.eventStream, "CassandraProjectionStorage")
  var initialized = new AtomicBoolean(false)
  val createKeyspaceStmt = """
      CREATE KEYSPACE IF NOT EXISTS bookstore
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
    """

  val createTableStmt = """
      CREATE TABLE IF NOT EXISTS bookstore.projectionoffsets (
        identifier varchar primary key, offset bigint)  
  """  
  
  val poolingOptions = new PoolingOptions()
  val cluster = 
    Cluster.builder().
    addContactPoint("127.0.0.1").
    withPoolingOptions(poolingOptions).
    build()
    
  val session = cluster.connect()
  val keyspace:Future[Done] = session.executeAsync(createKeyspaceStmt ).map(toDone)
  val result = 
    for{
      _ <- keyspace
      done <- session.executeAsync(createTableStmt)
    } yield done
    
  result onComplete{
    case util.Success(_) =>
      log.info("Successfully initialized the projection storage system")
      initialized.set(true)
      
    case util.Failure(ex) =>
      log.error(ex, "Error initializing projection storage system")
  }
    
  def updateOffset(identifier:String, offset:Long):Future[Done] = {
    val fut:Future[Done] = 
      session.executeAsync(s"update bookstore.projectionoffsets set offset = $offset where identifier = '$identifier'").map(toDone)
    fut
  }
  
  def toDone(a:Any):Done = Done

}
object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider { 
  override def lookup = CassandraProjectionStorage 
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system)
}