import scalax.file.defaultfs.DefaultPath
import scalax.file.Path
import scopt.immutable.OptionParser

object BenchmarkConfig {
  val parser = new OptionParser[BenchmarkConfig]("benchmark") {
    def options = Seq(
      opt("resources-dir", "benchmark resources folder - contains jars and repos") {
        (v: String, c: BenchmarkConfig) => c.copy(resourcesDirOption = Path.fromString(v))
      },
      opt("versions", "BFG versions to time - bfg-[version].jar - eg 1.4.0,1.5.0,1.6.0") {
        (v: String, c: BenchmarkConfig) => c.copy(bfgVersions = v.split(",").toSeq)
      },
      opt("repos", "Sample repos to test, eg github-gems,jgit,git") {
        (v: String, c: BenchmarkConfig) => c.copy(repoNames = v.split(",").toSeq)
      },
      opt("scratch-dir", "Temp-dir for job runs - preferably ramdisk, eg tmpfs.") {
        (v: String, c: BenchmarkConfig) => c.copy(scratchDir = Path.fromString(v))
      }
    )
  }
}
case class BenchmarkConfig(resourcesDirOption: Path = Path.fromString(System.getProperty("user.dir")) / "bfg-benchmark" / "resources",
                           scratchDir: DefaultPath = Path.fromString("/dev/shm/"),
                           bfgVersions: Seq[String] = Seq.empty,
                           repoNames: Seq[String] = Seq.empty) {

  lazy val resourcesDir = Path.fromString(resourcesDirOption.path).toAbsolute

  lazy val jarsDir = resourcesDir / "jars"

  lazy val reposDir = resourcesDir / "repos"

  lazy val bfgJars = bfgVersions.map(version => jarsDir / s"bfg-$version.jar")

  lazy val repoSpecDirs = repoNames.map(reposDir / _)
}
