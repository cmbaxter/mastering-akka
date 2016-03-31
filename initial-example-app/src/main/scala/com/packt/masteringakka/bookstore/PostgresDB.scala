package com.packt.masteringakka.bookstore

import com.typesafe.config.ConfigFactory
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult

object PostgresDB {
  val db = {
    val cfg = """
      mydb{
        numThreads = 10
        driver = "org.postgresql.Driver"
        url = "jdbc:postgresql://localhost:5432/akkaexampleapp"
        user = "root"
        password = ""
        connectionTestQuery = "select 1"
      }    
    """
  
    val conf = ConfigFactory.parseString(cfg)    
    Database.forConfig("mydb", conf)
  }
}

trait BookstoreDao{
  val db = PostgresDB.db
  
  
}