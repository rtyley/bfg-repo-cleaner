import sbt._

object Dependencies {

  val scalaGitVersion = "2.9"

  val scalaGit = "com.madgag.scala-git" %% "scala-git" % scalaGitVersion

  val scalaGitTest = "com.madgag.scala-git" %% "scala-git-test" % scalaGitVersion

  val specs2 = "org.specs2" %% "specs2" % "2.3.12"

  val madgagCompress = "com.madgag" % "util-compress" % "1.33"

  val textmatching = "com.madgag" %% "scala-textmatching" % "2.0"

  val scopt = "com.github.scopt" %% "scopt" % "3.2.0"

  val guava = Seq("com.google.guava" % "guava" % "18.0", "com.google.code.findbugs" % "jsr305" % "2.0.3")

  val scalaLogging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback" %  "logback-classic" % "1.1.3"
  )

  val scalaIoFile = "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1"

}
