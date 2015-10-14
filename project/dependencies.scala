import sbt._

object Dependencies {

  val scalaGitVersion = "3.3"

  val jgitVersionOverride = Option(System.getProperty("jgit.version"))

  val jgitVersion = jgitVersionOverride.getOrElse("4.1.0.201509280440-r")

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion

  val scalaGit = "com.madgag.scala-git" %% "scala-git" % scalaGitVersion exclude("org.eclipse.jgit", "org.eclipse.jgit")

  val scalaGitTest = "com.madgag.scala-git" %% "scala-git-test" % scalaGitVersion

  val specs2 = "org.specs2" %% "specs2" % "2.3.12"

  val madgagCompress = "com.madgag" % "util-compress" % "1.33"

  val textmatching = "com.madgag" %% "scala-textmatching" % "2.0"

  val scopt = "com.github.scopt" %% "scopt" % "3.2.0"

  val guava = Seq("com.google.guava" % "guava" % "18.0", "com.google.code.findbugs" % "jsr305" % "2.0.3")

  val scalaIoFile = "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1"

}
