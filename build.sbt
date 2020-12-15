import Dependencies._
import common._

organization in ThisBuild := "com.madgag"

scalaVersion in ThisBuild := "2.13.4"

scalacOptions in ThisBuild ++= Seq("-feature", "-language:postfixOps")

licenses in ThisBuild := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

homepage in ThisBuild := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

resolvers in ThisBuild ++= jgitVersionOverride.map(_ => Resolver.mavenLocal).toSeq

libraryDependencies in ThisBuild += scalatest % Test

lazy val root = Project(id = "bfg-parent", base = file(".")) aggregate (bfg, bfgTest, bfgLibrary)

releaseSignedArtifactsSettings

lazy val bfgTest = bfgProject("bfg-test")

lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(bfgTest % Test)

lazy val bfg = bfgProject("bfg") enablePlugins(BuildInfoPlugin) dependsOn(bfgLibrary, bfgTest % Test)

lazy val bfgBenchmark = bfgProject("bfg-benchmark")

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild := sonatypePublishToBundle.value

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
