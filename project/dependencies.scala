import sbt._

object Dependencies {

  val scalaGitVersion = "5.0.3"

  val jgitVersionOverride = Option(System.getProperty("jgit.version"))

  val jgitVersion = jgitVersionOverride.getOrElse("6.10.0.202406032230-r")

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion

  // this matches slf4j-api in jgit's dependencies
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % "1.7.36"

  val scalaCollectionPlus =  "com.madgag" %% "scala-collection-plus" % "0.11"

  val parCollections = "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0"

  val scalaGit = "com.madgag.scala-git" %% "scala-git" % scalaGitVersion exclude("org.eclipse.jgit", "org.eclipse.jgit")

  val scalaGitTest = "com.madgag.scala-git" %% "scala-git-test" % scalaGitVersion

  val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

  val madgagCompress = "com.madgag" % "util-compress" % "1.35"

  val textmatching = "com.madgag" %% "scala-textmatching" % "2.8"

  val scopt = "com.github.scopt" %% "scopt" % "3.7.1"

  val guava = Seq("com.google.guava" % "guava" % "33.4.0-jre", "com.google.code.findbugs" % "jsr305" % "3.0.2")

  val useNewerJava =  "com.madgag" % "use-newer-java" % "1.0.2"

  val lineSplitting = "com.madgag" %% "line-break-preserving-line-splitting" % "0.1.6"

}
