name := "kibana-query"

version := "0.1.0"

scalaVersion := "2.12.4"

val playVersion = "2.6.11"
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % playVersion,
  "com.typesafe.play" %% "play-ahc-ws" % playVersion
)
