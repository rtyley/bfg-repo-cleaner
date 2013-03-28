import AssemblyKeys._

assemblySettings

crossPaths := false

publishArtifact in (Compile, packageBin) := false

// replace the conventional main artifact with an uber-jar
addArtifact(artifact in (Compile, packageBin), assembly)

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "2.1.0"
)
