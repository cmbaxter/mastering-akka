package com.packt.masteringakka.bookstore.common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor._
import com.datastax.driver.core._

/**
 * Interface into a projection's offset storage system so that it can be properly resumed
 */
abstract class ResumableProjection(entityType:String){
  def storeLatestOffset(offset:Long)(implicit ex:ExecutionContext):Future[Boolean]
  def fetchLatestOffset(implicit ex:ExecutionContext):Future[Long]
}

object ResumableProjection{
  def apply(entityType:String) = new CassandraResumableProjection(entityType)
}

class CassandraResumableProjection(entityType:String) extends ResumableProjection(entityType){
  def storeLatestOffset(offset:Long)(implicit ex:ExecutionContext):Future[Boolean] = null
  def fetchLatestOffset(implicit ex:ExecutionContext):Future[Long] = null
}

class CassandraProjectionStorageExt(conf:Config) extends Extension{
  val poolingOptions = new PoolingOptions()
  val cluster = 
    Cluster.builder().
    addContactPoint("127.0.0.1").
    withPoolingOptions(poolingOptions).
    build()
    
  val session = cluster.connect()
}
object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider { 
  override def lookup = CassandraProjectionStorage 
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system.settings.config)
}