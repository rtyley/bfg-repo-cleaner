import com.typesafe.sbt.pgp.PgpKeys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object common {
  lazy val releaseSignedArtifactsSettings = Seq(
    releaseProcess ~= {
      s: Seq[ReleaseStep] =>
        lazy val publishArtifactsAction = { st: State =>
          val extracted = Project.extract(st)
          val ref = extracted.get(Keys.thisProjectRef)
          extracted.runAggregated(publishSigned in Global in ref, st)
        }

        s map {
          case `publishArtifacts` => publishArtifacts.copy(action = publishArtifactsAction)
          case step => step
        } map {
          _.copy(enableCrossBuild = false)
        }
    }
  )

  def bfgProject(name: String) = Project(name, file(name)) settings releaseSignedArtifactsSettings
}
