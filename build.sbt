name := "weatherserver"

version := "0.1"

scalaVersion := "2.13.10"

val http4sVersion = "0.23.18"
val circeVersion = "0.14.5"
val log4catsVersion = "2.6.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",
  "ch.qos.logback" % "logback-classic" % "0.9.28",
  "org.typelevel" %% "log4cats-core" % log4catsVersion,
  "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
  "org.mockito" %% "mockito-scala" % "1.16.42" % Test,
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.6" % Test
)