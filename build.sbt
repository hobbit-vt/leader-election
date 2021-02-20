scalaVersion := "2.12.13"

organization := "com.github.hobbitvt"

name := "leader-election"

version := "0.3.0"

resolvers += Resolver.bintrayRepo("hobbit-vt", "maven")
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

libraryDependencies ++= Seq(
  "org.asynchttpclient"         % "async-http-client" % "2.12.2",
  "io.monix"                   %% "monix"             % "3.3.0",
  "io.circe"                   %% "circe-parser"      % "0.13.0",
  "com.typesafe.scala-logging" %% "scala-logging"     % "3.9.2",
  "org.scalatest"              %% "scalatest"         % "3.2.0-SNAP6" % "test"
)
