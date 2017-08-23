scalaVersion := "2.12.3"

organization := "com.github.hobbitvt"

name := "leader-election"

version := "0.1.0"

libraryDependencies ++= Seq(
  "net.databinder.dispatch"     %% "dispatch-core"    % "0.13.1",
  "io.monix"                    %% "monix-execution"  % "2.3.0",
  "io.circe"                    %% "circe-parser"     % "0.8.0",
  "com.typesafe.scala-logging"  %% "scala-logging"    % "3.5.0",
  "org.scalatest"               %% "scalatest"        % "3.2.0-SNAP6"   % "test"
)
