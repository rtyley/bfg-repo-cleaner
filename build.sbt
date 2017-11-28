import Dependencies._
import common._
import Defaults._
import com.typesafe.sbt.pgp.PgpKeys._

organization in ThisBuild := "com.madgag"

scalaVersion in ThisBuild := "2.12.4"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-language:postfixOps")

licenses in ThisBuild := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

homepage in ThisBuild := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

resolvers in ThisBuild ++= jgitVersionOverride.map(_ => Resolver.mavenLocal).toSeq

libraryDependencies in ThisBuild += scalatest % "test"

lazy val root = Project(id = "bfg-parent", base = file(".")) aggregate (bfg, bfgTest, bfgLibrary)

releaseSignedArtifactsSettings

publishSigned := {}

lazy val bfgTest = bfgProject("bfg-test")

lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(bfgTest % "test")

lazy val bfg = bfgProject("bfg") enablePlugins(BuildInfoPlugin) dependsOn(bfgLibrary, bfgTest % "test")

lazy val bfgBenchmark = bfgProject("bfg-benchmark")

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild :=
  Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild := (
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
