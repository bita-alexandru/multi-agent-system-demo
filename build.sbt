ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.1"

lazy val root = (project in file("."))
  .settings(
    name := "multi-agent-system-demo",
    idePackagePrefix := Some("alex.demo")
  )

val PekkoVersion = "1.2.0"
libraryDependencies += "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion

libraryDependencies += "io.circe" %% "circe-yaml" % "1.15.0"
libraryDependencies += "io.circe" %% "circe-generic" % "0.14.14"
libraryDependencies += "io.circe" %% "circe-parser" % "0.14.14"
libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.10"
libraryDependencies += "io.github.cdimascio" % "dotenv-java" % "3.2.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18"
