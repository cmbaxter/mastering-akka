import NativePackagerHelper._

enablePlugins(JavaServerAppPackaging)

name := "initial-example-app"

lazy val commonSettings = Seq(
  organization := "com.packt.masteringakka",
  version := "0.1.0",
  scalaVersion := "2.11.2"
)

lazy val root = (project in file(".")).
  aggregate(common, bookServices, userServices, creditServices, orderServices, server)

lazy val common = (project in file("common")).
  settings(commonSettings: _*)

lazy val bookServices = (project in file("book-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val userServices = (project in file("user-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val creditServices = (project in file("credit-services")).
  settings(commonSettings: _*).
  dependsOn(common)

lazy val orderServices = (project in file("order-services")).
  settings(commonSettings: _*).
  dependsOn(common)      

lazy val server = (project in file("server")).
  settings(commonSettings: _*).
  dependsOn(common, bookServices, userServices, creditServices, orderServices)


mappings in Universal ++= {
  directory("scripts") ++
  contentOf("src/main/resources").toMap.mapValues("config/" + _)
}

scriptClasspath := Seq("../config/") ++ scriptClasspath.value