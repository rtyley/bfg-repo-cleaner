import sbt._
import Defaults._
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtrelease.ReleaseStateTransformations._

object BFGBuild extends Build {
  lazy val root = Project(id = "bfg-parent", base = file(".")) settings (signedReleaseSettings:_*) settings (
    publishSigned := {} ) aggregate(textmatching, scalaGitTest, scalaGit, bfg, bfgLibrary, bfgBenchmark)

  lazy val bfg = bfgProject("bfg") dependsOn(bfgLibrary, scalaGitTest % "test")

  lazy val textmatching = bfgProject("textmatching")

  lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(textmatching, scalaGit, scalaGitTest % "test")

  lazy val bfgBenchmark = bfgProject("bfg-benchmark") dependsOn(textmatching)

  lazy val scalaGit = bfgProject("scala-git") dependsOn (scalaGitTest % "test")

  lazy val scalaGitTest = bfgProject("scala-git-test")

  lazy val signedReleaseSettings = releaseSettings ++ Seq(
    releaseProcess ~= {
      s: Seq[ReleaseStep] =>
        lazy val publishArtifactsAction = { st: State =>
          val extracted = Project.extract(st)
          val ref = extracted.get(Keys.thisProjectRef)
          extracted.runAggregated(publishSigned in Global in ref, st)
        }

        s map {
          case `publishArtifacts` => publishArtifacts.copy(action = publishArtifactsAction)
          case s => s
        }
    }
  )

  def bfgProject(name: String) = Project(name, file(name), settings = {
    defaultSettings ++ signedReleaseSettings
  })

}
