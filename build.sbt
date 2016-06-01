name := "scodec-playground"
version := "1.0"
scalaVersion := "2.11.8"

libraryDependencies ++= List(
  "org.scodec" %% "scodec-core" % "1.9.0",
  "org.scodec" %% "scodec-bits" % "1.1.0"
)
libraryDependencies ++= List(
  "com.typesafe.akka" %% "akka-stream" % "2.4.6"
)

tutSettings
