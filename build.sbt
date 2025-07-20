ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "pekko-multi-agent-system",
    idePackagePrefix := Some("demo.alex.application")
  )

val PekkoVersion = "1.1.5"
libraryDependencies += "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion
