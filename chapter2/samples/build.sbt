name := "chapter2-samples"

organization := "com.packt.masteringakka"

version := "0.1.0"

scalaVersion := "2.11.2"
 
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.4",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.4"
)
