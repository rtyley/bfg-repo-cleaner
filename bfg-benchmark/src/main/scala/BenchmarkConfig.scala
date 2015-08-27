import java.io.File

import com.madgag.textmatching.{Glob, TextMatcher}
import scopt.OptionParser

import scalax.file.ImplicitConversions._
import scalax.file.Path
import scalax.file.defaultfs.DefaultPath

object BenchmarkConfig {
  val parser = new OptionParser[BenchmarkConfig]("benchmark") {
    opt[File]("resources-dir").text("benchmark resources folder - contains jars and repos").action {
      (v, c) => c.copy(resourcesDirOption = v)
    }
    opt[String]("java").text("Java command paths").action {
      (v, c) => c.copy(javaCmds = v.split(',').toSeq)
    }
    opt[String]("versions").text("BFG versions to time - bfg-[version].jar - eg 1.4.0,1.5.0,1.6.0").action {
      (v, c) => c.copy(bfgVersions = v.split(",").toSeq)
    }
    opt[Int]("die-if-longer-than").text("Useful for git-bisect").action {
      (v, c) => c.copy(dieIfTaskTakesLongerThan = Some(v))
    }
    opt[String]("repos").text("Sample repos to test, eg github-gems,jgit,git").action {
      (v, c) => c.copy(repoNames = v.split(",").toSeq)
    }
    opt[String]("commands").valueName("<glob>").text("commands to exercise").action {
      (v, c) => c.copy(commands = TextMatcher(v, defaultType = Glob))
    }
    opt[File]("scratch-dir").text("Temp-dir for job runs - preferably ramdisk, eg tmpfs.").action {
      (v, c) => c.copy(scratchDir = v)
    }
    opt[Unit]("only-bfg") action { (_, c) => c.copy(onlyBfg = true) } text "Don't benchmark git-filter-branch"
  }
}
case class BenchmarkConfig(resourcesDirOption: Path = Path.fromString(System.getProperty("user.dir")) / "bfg-benchmark" / "resources",
                           scratchDir: DefaultPath = Path.fromString("/dev/shm/"),
                           javaCmds: Seq[String] = Seq("java"),
                           bfgVersions: Seq[String] = Seq.empty,
                           commands: TextMatcher = Glob("*"),
                           onlyBfg: Boolean = false,
                           dieIfTaskTakesLongerThan: Option[Int] = None,
                           repoNames: Seq[String] = Seq.empty) {

  lazy val resourcesDir = Path.fromString(resourcesDirOption.path).toAbsolute

  lazy val jarsDir = resourcesDir / "jars"

  lazy val reposDir = resourcesDir / "repos"

  lazy val bfgJars = bfgVersions.map(version => jarsDir / s"bfg-$version.jar")

  lazy val repoSpecDirs = repoNames.map(reposDir / _)
}
