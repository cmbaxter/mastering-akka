import NativePackagerHelper._

name := "chapter8-bookstore-app-complete"

lazy val commonSettings = Seq(
  organization := "com.packt.masteringakka",
  version := "0.1.0",
  scalaVersion := "2.11.2"
)

lazy val root = (project in file(".")).
  aggregate(common, inventoryMgmt, userMgmt, creditProcessing, salesOrderProcessing, server)

lazy val common = (project in file("common")).
  settings(commonSettings: _*)

lazy val inventoryMgmt = (project in file("inventory-management")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val userMgmt = (project in file("user-management")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val creditProcessing = (project in file("credit-processing")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val salesOrderProcessing = (project in file("sales-order-processing")).
  settings(commonSettings: _*).
  dependsOn(common, inventoryMgmt, userMgmt, creditProcessing)      

lazy val server = Project(
    id = "server",
    base = file("server"),    
    settings = commonSettings ++ packageArchetype.java_server ++ Seq(
        mainClass in Compile := Some("com.packt.masteringakka.bookstore.server.Server")
    )
) dependsOn(common, inventoryMgmt, userMgmt, creditProcessing, salesOrderProcessing)