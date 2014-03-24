import java.util.concurrent.TimeUnit._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.sys.process._
import com.madgag.compress.CompressUtil._
import scalax.file.defaultfs.DefaultPath
import scalax.file.Path
import scalax.file.ImplicitConversions._
import java.lang.System.nanoTime
import scalax.file.PathMatcher.IsDirectory
import scalax.io.{Input, Codec}
import scala.concurrent._
import ExecutionContext.Implicits.global

object Benchmark extends App {

  implicit val codec = Codec.UTF8

  BenchmarkConfig.parser.parse(args, BenchmarkConfig()) map {
    config =>
      println(s"Using resources dir : ${config.resourcesDir.path}")

      require(config.resourcesDir.exists, s"Resources dir not found : ${config.resourcesDir.path}")
      require(config.jarsDir.exists, s"Jars dir not found : ${config.jarsDir.path}")
      require(config.reposDir.exists, s"Repos dir not found : ${config.reposDir.path}")

      val missingJars = config.bfgJars.filterNot(_.exists).map(_.toAbsolute.path)
      require(missingJars.isEmpty, s"Missing BFG jars : ${missingJars.mkString(",")}")

      def javaVersions(): Future[Map[String, String]] =
        Future.traverse(config.javaCmds)(jc => JavaVersion.version(jc).map(jc -> _)).map(_.toMap)

      def extractRepoFrom(zipPath: Path) = {
        val repoDir = config.scratchDir / "repo.git"

        repoDir.deleteRecursively(force = true)

        repoDir.createDirectory()

        println(s"Extracting repo to ${repoDir.toAbsolute.path}")

        zipPath.inputStream.acquireFor { stream => unzip(stream, repoDir) }

        repoDir
      }

      for (jvs <- javaVersions()) {
        config.repoSpecDirs.foreach {
          repoSpecDir =>
            val repoName = repoSpecDir.name

            println(s"Repo : $repoName")

            val availableCommandDirs = (repoSpecDir / "commands").children().filter(IsDirectory)

            println(s"Available commands for $repoName : ${availableCommandDirs.map(_.name).mkString(", ")}")

            availableCommandDirs.filter(p => config.commands(p.name)).foreach {
              commandDir =>

                val commandName = commandDir.name

                def runJobFor(typ: String, processGen: ProcessGen): Option[Duration] = {
                  val paramsPath = commandDir / s"$typ.txt"
                  if (paramsPath.exists) {
                    val repoDir: DefaultPath = extractRepoFrom(repoSpecDir / "repo.git.zip")
                    commandDir.children().foreach(p => p.copyTo(repoDir / p.name))
                    val process = processGen.genProcess(paramsPath, repoDir)
                    Some(measureTask(s"$commandName - ${processGen.description}") {
                      process ! ProcessLogger(_ => Unit)
                    })
                  } else None
                }

                val bfgExecutions: Seq[(String, Duration)] = (for {
                  bfgJar <- config.bfgJars
                  (javaCmd, javaVersion) <- jvs
                } yield {
                  val desc = s"${bfgJar.simpleName}_java-$javaVersion"
                  val duration = runJobFor("bfg", new ProcessGen {
                    def genProcess(paramsInput: Input, repoPath: DefaultPath) =
                      Process(s"$javaCmd -jar ${bfgJar.path} ${paramsInput.string}", repoPath)

                    val description = desc
                  })
                  duration.map(d => desc -> d)
                }).flatten

                val gfbDuration: Option[Duration] = if (config.onlyBfg) None
                else runJobFor("gfb", new ProcessGen {
                  lazy val description = "git filter-branch"

                  def genProcess(paramsInput: Input, repoPath: DefaultPath) =
                    Process(Seq("git", "filter-branch") ++ paramsInput.lines(), repoPath)
                })

                val samples = TaskExecutionSamples(bfgExecutions, gfbDuration)
                println(s"$repoName $commandName :: ${samples.summary}")
            }
        }
      }
  }


  case class TaskExecutionSamples(bfgExecutions: Seq[(String, Duration)], gfbExecution: Option[Duration]) {

    lazy val summary = {
      bfgExecutions.map { case (name,dur) => f"$name: ${dur.toMillis}%,d ms"}.mkString(", ") + gfbExecution.map {
        gfb => "  "+bfgExecutions.map { case (name,dur) => f"$name: ${gfb/dur}%2.1fx"}.mkString(", ")
      }.getOrElse("")
    }
  }

  def measureTask[T](description: String)(block: => T): Duration = {
    val start = nanoTime
    val result = block
    val duration = FiniteDuration(nanoTime - start, NANOSECONDS)
    println(s"$description completed in %,d ms.".format(duration.toMillis))
    duration
  }


  case class BFGExecution(bfgJar: Path, bfgParams: Path, repoDir: Path)

  trait ProcessGen {
    val description: String

    def genProcess(paramsInput: Input, repoPath: DefaultPath): ProcessBuilder
  }
}
