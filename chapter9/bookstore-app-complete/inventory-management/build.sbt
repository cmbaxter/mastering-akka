import ByteConversions._

name := "chapter9-inventory-management"
organization := "com.packt.masteringakka"
version := "0.1.0"

scalaVersion := "2.11.2"

libraryDependencies ++= {  
  Seq(
    "com.packt.masteringakka" %% "chapter9-bookstore-common" % "0.1.0-SNAPSHOT"
  )
}

normalizedName in Bundle := "inventory"

BundleKeys.system := "BookstoreSystem"

BundleKeys.endpoints := Map(
  "akka-remote" -> Endpoint("tcp"),
  "inventory-management" -> Endpoint("http", 0, Set(URI("http://:9000/inventory")))
)

BundleKeys.startCommand += "-main com.packt.masteringakka.bookstore.inventory.Main"

javaOptions in Universal := Seq(
  "-J-Xmx256m",
  "-J-Xms256m"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 512.MiB
BundleKeys.diskSpace := 50.MB

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)