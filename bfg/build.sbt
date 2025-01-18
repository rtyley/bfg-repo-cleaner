import java.io.{File, FileOutputStream}

import Dependencies.*
import sbt.taskKey

import scala.sys.process.Process
import scala.util.Try

val gitDescription = taskKey[String]("Git description of working dir")

gitDescription := Try[String](Process("git describe --all --always --dirty --long").lineStream.head.replace("heads/","").replace("-0-g","-")).getOrElse("unknown")

libraryDependencies += useNewerJava

mainClass := Some("use.newer.java.Version8")
Compile / packageBin / packageOptions +=
  Package.ManifestAttributes( "Main-Class-After-UseNewerJava-Check" -> "com.madgag.git.bfg.cli.Main" )

// note you don't want the jar name to collide with the non-assembly jar, otherwise confusion abounds.
assembly / assemblyJarName := s"${name.value}-${version.value}-${gitDescription.value}${jgitVersionOverride.map("-jgit-" + _).mkString}.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, gitDescription)

buildInfoPackage := "com.madgag.git.bfg"

crossPaths := false

Compile / packageBin / publishArtifact := false

// replace the conventional main artifact with an uber-jar
addArtifact(Compile / packageBin / artifact, assembly)

val cliUsageDump = taskKey[File]("Dump the CLI 'usage' output to a file")

cliUsageDump := {
  val usageDumpFile = File.createTempFile("bfg-usage", "dump.txt")
  val scalaRun = new ForkRun(ForkOptions().withOutputStrategy(CustomOutput(new FileOutputStream(usageDumpFile))))

  val mainClassName = (Compile / run / mainClass).value getOrElse sys.error("No main class detected.")
  val classpath = Attributed.data((Runtime / fullClasspath).value)
  val args = Seq.empty

  scalaRun.run(mainClassName, classpath, args, streams.value.log).failed foreach (sys error _.getMessage)
  usageDumpFile
}

addArtifact( Artifact("bfg", "usage", "txt"), cliUsageDump )

libraryDependencies ++= Seq(
  scopt,
  jgit,
  scalaGitTest % "test"
)

import Tests.*
{
  def isolateTestsWhichRequireTheirOwnJvm(tests: Seq[TestDefinition]) = {
    val (testsRequiringIsolation, testsNotNeedingIsolation) = tests.partition(_.name.contains("RequiresOwnJvm"))

    val groups: Seq[Seq[TestDefinition]] = testsRequiringIsolation.map(Seq(_)) :+ testsNotNeedingIsolation

    groups map { group =>
      Group(group.size.toString, group, SubProcess(ForkOptions()))
    }
  }

  Test / testGrouping := isolateTestsWhichRequireTheirOwnJvm( (Test / definedTests).value )
}

Test / fork := true // JGit uses static (ie JVM-wide) config

Test / logBuffered := false

Test / parallelExecution := false

