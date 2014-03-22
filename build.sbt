import Dependencies._
import common._
import sbt._
import Defaults._
import com.typesafe.sbt.pgp.PgpKeys._

organization in ThisBuild := "com.madgag"

scalaVersion in ThisBuild := "2.10.3"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-language:postfixOps")

licenses in ThisBuild := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

homepage in ThisBuild := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

libraryDependencies in ThisBuild += specs2 % "test"

lazy val root = Project(id = "bfg-parent", base = file(".")) settings (signedReleaseSettings:_*) settings (
    publishSigned := {} ) aggregate(textmatching, scalaGitTest, scalaGit, bfg, bfgLibrary)

lazy val bfg = bfgProject("bfg") dependsOn(bfgLibrary, scalaGitTest % "test")

lazy val textmatching = bfgProject("textmatching")

lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(textmatching, scalaGit, scalaGitTest % "test")

lazy val bfgBenchmark = bfgProject("bfg-benchmark") dependsOn(textmatching)

lazy val scalaGit = bfgProject("scala-git") dependsOn (scalaGitTest % "test")

lazy val scalaGitTest = bfgProject("scala-git-test")

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at s"${nexus}content/repositories/snapshots")
  else
    Some("releases"  at s"${nexus}service/local/staging/deploy/maven2")
}

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
    </developers>)
