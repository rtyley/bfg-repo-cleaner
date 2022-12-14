import sbt._

object Dependencies {

  val scalaGitVersion = "4.3"

  val jgitVersionOverride = Option(System.getProperty("jgit.version"))

  val jgitVersion = jgitVersionOverride.getOrElse("4.4.1.201607150455-r")

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion

  // the 1.7.2 here matches slf4j-api in jgit's dependencies
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % "1.7.2"

  val scalaCollectionPlus =  "com.madgag" %% "scala-collection-plus" % "0.11"

  val parCollections = "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.0"

  val scalaGit = "com.madgag.scala-git" %% "scala-git" % scalaGitVersion exclude("org.eclipse.jgit", "org.eclipse.jgit")

  val scalaGitTest = "com.madgag.scala-git" %% "scala-git-test" % scalaGitVersion

  val scalatest = "org.scalatest" %% "scalatest" % "3.2.14"

  val madgagCompress = "com.madgag" % "util-compress" % "1.35"

  val textmatching = "com.madgag" %% "scala-textmatching" % "2.8"

  val scopt = "com.github.scopt" %% "scopt" % "4.1.0"

  val guava = Seq("com.google.guava" % "guava" % "30.1-jre", "com.google.code.findbugs" % "jsr305" % "2.0.3")

  val useNewerJava =  "com.madgag" % "use-newer-java" % "0.8"

  val lineSplitting = "com.madgag" %% "line-break-preserving-line-splitting" % "0.1.4"

}
