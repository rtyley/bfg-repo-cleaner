import AssemblyKeys._

name := "bfg-repo-cleaner"

organization := "com.madgag"

assemblySettings

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "6.0.4",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.1",
  "com.google.guava" % "guava" % "13.0.1", "com.google.code.findbugs" % "jsr305" % "1.3.+",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "2.1.0.201209190230-r",
  "com.github.scopt" %% "scopt" % "2.1.0",
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "com.madgag" % "util-compress" % "1.33" % "test"
)

artifact in(Compile, assembly) ~= {
  art =>
    art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in(Compile, assembly), assembly)
