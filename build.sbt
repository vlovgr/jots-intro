val http4sVersion = "0.23.39"
val jotsVersion = "0.4.0"
val slf4jVersion = "1.7.36"

scalaVersion := "3.10.1"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.slf4j" % "slf4j-nop" % slf4jVersion,
  "se.vlovgr" %% "jots" % jotsVersion,
  "se.vlovgr" %% "jots-http4s" % jotsVersion,
  "se.vlovgr" %% "jots-testing" % jotsVersion
)
