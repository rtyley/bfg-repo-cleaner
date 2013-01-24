import AssemblyKeys._

name := "bfg-repo-cleaner"

organization := "com.madgag"

licenses := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

homepage := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

scalaVersion := "2.10.0"

scalacOptions += "-language:implicitConversions"

assemblySettings

releaseSettings

crossPaths := false

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra := (
    <scm>
      <url>git@github.com:rtyley/bfg-repo-cleaner.git</url>
      <connection>scm:git:git@github.com:rtyley/bfg-repo-cleaner.git</connection>
    </scm>
    <developers>
      <developer>
        <id>rtyley</id>
        <name>Roberto Tyley</name>
        <url>https://github.com:rtyley</url>
      </developer>
    </developers>)

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
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
  "com.ibm.icu" % "icu4j" % "50.1.1",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.madgag" % "util-compress" % "1.33" % "test"
)

artifact in(Compile, assembly) ~= { _.copy(name = "bfg") }

addArtifact(artifact in(Compile, assembly), assembly)
