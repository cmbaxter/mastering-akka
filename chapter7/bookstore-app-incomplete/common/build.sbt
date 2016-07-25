name := "chapter7-bookstore-common-incomplete"

libraryDependencies ++= {
  val akkaVersion = "2.4.8"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.14" excludeAll(ExclusionRule("io.netty")),
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion excludeAll(ExclusionRule("io.netty")),
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,    
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,  
    "ch.qos.logback" % "logback-classic" % "1.0.9",
    "net.databinder" %% "unfiltered-filter" % "0.8.4",
    "net.databinder" %% "unfiltered-netty" % "0.8.4",
    "net.databinder" %% "unfiltered-netty-server" % "0.8.4",
    "net.databinder" %% "unfiltered-json4s" % "0.8.4",
    "org.json4s" %% "json4s-ext" % "3.2.9",
    "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
    "com.google.protobuf" % "protobuf-java"  % "2.5.0",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion % Test
  )
}