import AssemblyKeys._

name := "bfg-repo-cleaner"

organization := "com.madgag"

scalaVersion := "2.10.0"

assemblySettings

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "6.0.4",
  "com.google.guava" % "guava" % "13.0.1", "com.google.code.findbugs" % "jsr305" % "2.0.1",
  "com.madgag" % "org.eclipse.jgit" % "2.2.0.0.2-UNOFFICIAL-ROBERTO-RELEASE",
  "com.github.scopt" %% "scopt" % "2.1.0",
  "com.madgag" % "globs-for-java" % "0.2",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.madgag" % "util-compress" % "1.33" % "test"
)

artifact in(Compile, assembly) ~= {
  art =>
    art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in(Compile, assembly), assembly)
