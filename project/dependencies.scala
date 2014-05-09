import sbt._

object Dependencies {

  val scalaGitVersion = "2.2"

  val scalaGit = "com.madgag.scala-git" %% "scala-git" % scalaGitVersion

  val scalaGitTest = "com.madgag.scala-git" %% "scala-git-test" % scalaGitVersion

  val specs2 = "org.specs2" %% "specs2" % "2.3.11"

  val madgagCompress = "com.madgag" % "util-compress" % "1.33"

  val textmatching = "com.madgag" %% "scala-textmatching" % "2.0"

  val scopt = "com.github.scopt" %% "scopt" % "3.2.0"

  val guava = Seq("com.google.guava" % "guava" % "16.0.1", "com.google.code.findbugs" % "jsr305" % "2.0.1")

  val scalaIoFile = "com.madgag" %% "scala-io-file" % "0.4.2"

}
