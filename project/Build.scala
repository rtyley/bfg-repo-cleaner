import sbt._
import Defaults._
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtrelease.ReleaseStateTransformations._
import Keys._

object BFGBuild extends Build {
  lazy val root = Project(id = "bfg-parent", base = file(".")) settings (signedReleaseSettings:_*) settings ( publishSigned := {} ) aggregate(scalaGitTest, scalaGit, bfg, bfgLibrary)

  lazy val bfg = bfgProject("bfg") dependsOn(bfgLibrary, scalaGitTest % "test")

  lazy val bfgLibrary = bfgProject("bfg-library") dependsOn(scalaGit, scalaGitTest % "test")

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
