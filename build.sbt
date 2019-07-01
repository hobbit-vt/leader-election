scalaVersion := "2.12.6"

organization := "com.github.hobbitvt"

name := "leader-election"

version := "0.2.0"

resolvers += Resolver.bintrayRepo("hobbit-vt", "maven")
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

libraryDependencies ++= Seq(
  "org.asynchttpclient"        % "async-http-client" % "2.1.0-alpha26",
  "io.monix"                   %% "monix"            % "3.0.0-RC3",
  "io.circe"                   %% "circe-parser"     % "0.10.0",
  "com.typesafe.scala-logging" %% "scala-logging"    % "3.5.0",
  "org.scalatest"              %% "scalatest"        % "3.2.0-SNAP6" % "test"
)
