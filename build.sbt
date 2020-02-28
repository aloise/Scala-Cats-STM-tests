name := "aloise-stm-tests"

version := "0.1"

scalaVersion := "2.13.1"

val Http4sVersion = "0.21.1"
val CirceVersion = "0.13.0"
val LogbackVersion = "1.2.3"
val fs2Version = "2.2.2"

lazy val root = (project in file("."))
  .settings(
    organization := "name.aloise",
    name := "quickstart-garden-docker",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"      %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
      "co.fs2" %% "fs2-core" % fs2Version,
      "io.github.timwspence" %% "cats-stm" % "0.7.0",
      "com.github.pureconfig" %% "pureconfig" % "0.12.2",
      "org.scalatest" %% "scalatest" % "3.1.1" % Test,
      "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.0" % Test
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
)
