import Dependencies._
import common._

ThisBuild / organization := "com.madgag"

ThisBuild / scalaVersion := "2.13.10"

ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-language:postfixOps")

ThisBuild / licenses := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

ThisBuild / homepage := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

ThisBuild / resolvers ++= jgitVersionOverride.map(_ => Resolver.mavenLocal).toSeq

ThisBuild / libraryDependencies += scalatest % Test

ThisBuild / Test/testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-u", s"test-results/scala-${scalaVersion.value}"
)

lazy val root = Project(id = "bfg-parent", base = file(".")) aggregate (bfg, bfgTest, bfgLibrary)

releaseSignedArtifactsSettings

lazy val bfgTest = bfgProject("bfg-test")

lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(bfgTest % Test)

lazy val bfg = bfgProject("bfg") enablePlugins(BuildInfoPlugin) dependsOn(bfgLibrary, bfgTest % Test)

lazy val bfgBenchmark = bfgProject("bfg-benchmark")

ThisBuild / publishTo := sonatypePublishToBundle.value

ThisBuild / pomExtra := (
  <scm>
    <url>git@github.com:rtyley/bfg-repo-cleaner.git</url>
    <connection>scm:git:git@github.com:rtyley/bfg-repo-cleaner.git</connection>
  </scm>
    <developers>
      <developer>
        <id>rtyley</id>
        <name>Roberto Tyley</name>
        <url>https://github.com/rtyley</url>
      </developer>
    </developers>
)
