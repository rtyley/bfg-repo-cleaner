import AssemblyKeys._
import Dependencies._
import sbt.taskKey

assemblySettings

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

val gitDescription = taskKey[String]("Git description of working dir")

gitDescription := Process("git describe --all --always --dirty --long").lines.head
  .replace("heads/","").replace("-0-g","-")

// note you don't want the jar name to collide with the non-assembly jar, otherwise confusion abounds.
jarName in assembly := s"${name.value}-${version.value}-${gitDescription.value}.jar"

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, gitDescription)

buildInfoPackage := "com.madgag.git.bfg"

crossPaths := false

publishArtifact in (Compile, packageBin) := false

// replace the conventional main artifact with an uber-jar
addArtifact(artifact in (Compile, packageBin), assembly)

libraryDependencies ++= Seq(
  scopt,
  scalaGitTest % "test"
)