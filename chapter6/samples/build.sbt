name := "chapter6-samples"

organization := "com.packt.masteringakka"

version := "0.1.0"

scalaVersion := "2.11.8"
 
libraryDependencies ++= { 
  val akkaVersion = "2.4.6"
  Seq(
	  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
	  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "org.scalatest" %% "scalatest" % "2.2.6" % Test
  )
}
