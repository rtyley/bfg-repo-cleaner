import AssemblyKeys._
import Dependencies._

assemblySettings

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion)

buildInfoPackage := "com.madgag.git.bfg"

crossPaths := false

publishArtifact in (Compile, packageBin) := false

// replace the conventional main artifact with an uber-jar
addArtifact(artifact in (Compile, packageBin), assembly)

libraryDependencies += scopt