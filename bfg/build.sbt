import AssemblyKeys._
import Dependencies._

assemblySettings

crossPaths := false

publishArtifact in (Compile, packageBin) := false

// replace the conventional main artifact with an uber-jar
addArtifact(artifact in (Compile, packageBin), assembly)

libraryDependencies += scopt