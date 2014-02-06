import sbt._

object Dependencies {

  val jgit = "com.madgag" % "org.eclipse.jgit" % "2.99.99.2.0-UNOFFICIAL-ROBERTO-RELEASE"

  val specs2 = "org.specs2" %% "specs2" % "2.1.1"

  val madgagCompress = "com.madgag" % "util-compress" % "1.33"

  val scopt = "com.github.scopt" %% "scopt" % "3.2.0"

  val globs = "com.madgag" % "globs-for-java" % "0.2"

  val guava = Seq("com.google.guava" % "guava" % "16.0.1", "com.google.code.findbugs" % "jsr305" % "2.0.1")

  val scalaIoFile = "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2"

}
