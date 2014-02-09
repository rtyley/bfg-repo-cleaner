import sbt._
import Defaults._
import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import com.typesafe.sbt.pgp.PgpKeys._
import sbtrelease.ReleaseStateTransformations._

object common {

  val VersionEndingInSnapshot = """(.*)-SNAPSHOT""".r

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

  def bfgProject(name: String) = Project(name, file(name), settings = {defaultSettings ++ signedReleaseSettings})

}