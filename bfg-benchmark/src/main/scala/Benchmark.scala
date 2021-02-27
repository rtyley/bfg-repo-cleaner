import lib.Timing.measureTask
import lib._
import model._

import java.nio.file.Files
import java.nio.file.Files.isDirectory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.jdk.StreamConverters._
import scala.sys.process._

/*
 * Vary BFG runs by:
 * Java version
 * BFG version (JGit version?)
 *
 */
object Benchmark extends App {

  BenchmarkConfig.parser.parse(args, BenchmarkConfig()) map {
    config =>
      println(s"Using resources dir : ${config.resourcesDir}")

      require(Files.exists(config.resourcesDir), s"Resources dir not found : ${config.resourcesDir}")
      require(Files.exists(config.jarsDir), s"Jars dir not found : ${config.jarsDir}")
      require(Files.exists(config.reposDir), s"Repos dir not found : ${config.reposDir}")

      val missingJars = config.bfgJars.filterNot(Files.exists(_))
      require(missingJars.isEmpty, s"Missing BFG jars : ${missingJars.mkString(",")}")

      val tasksFuture = for {
        bfgInvocableEngineSet <- bfgInvocableEngineSet(config)
      } yield {
        val gfbInvocableEngineSetOpt =
          Option.when(!config.onlyBfg)(InvocableEngineSet[GFBInvocation](GitFilterBranch, Seq(InvocableGitFilterBranch)))
        boogaloo(config, new RepoExtractor(config.scratchDir), Seq(bfgInvocableEngineSet) ++ gfbInvocableEngineSetOpt.toSeq)
      }

      Await.result(tasksFuture, Duration.Inf)
  }

  def bfgInvocableEngineSet(config: BenchmarkConfig): Future[InvocableEngineSet[BFGInvocation]] = for {
      javas <- Future.traverse(config.javaCmds)(jc => JavaVersion.version(jc).map(v => Java(jc, v)))
    } yield {
      val invocables = for {
        java <- javas
        bfgJar <- config.bfgJars
      } yield InvocableBFG(java, BFGJar.from(bfgJar))

      InvocableEngineSet[BFGInvocation](BFG, invocables)
    }

  /*
 * A Task says "here is something you can do to a given repo, and here is how to do
 * it with a BFG, and with git-filter-branch"
 */
  def boogaloo(config: BenchmarkConfig, repoExtractor: RepoExtractor, invocableEngineSets: Seq[InvocableEngineSet[_ <: EngineInvocation]]) = {

    for {
      repoSpecDir <- config.repoSpecDirs
      availableCommandDirs = Files.list(repoSpecDir.resolve("commands")).toScala(Seq).filter(isDirectory(_))
      // println(s"Available commands for $repoName : ${availableCommandDirs.map(_.name).mkString(", ")}")
      commandDir <- availableCommandDirs.filter(p => config.commands(p.getFileName.toString))
    } yield {
      val commandName: String = commandDir.getFileName.toString
      
      commandName -> (for {
        invocableEngineSet <- invocableEngineSets
      } yield for {
          (invocable, processMaker) <- invocableEngineSet.invocationsFor(commandDir)
        } yield {
        val cleanRepoDir = repoExtractor.extractRepoFrom(repoSpecDir.resolve("repo.git.zip"))
        Files.list(commandDir).toScala(Seq).foreach(p => Files.copy(p, cleanRepoDir.resolve(p.getFileName)))
        val process = processMaker(cleanRepoDir.toFile)

            val duration = measureTask(s"$commandName - $invocable") {
              process ! ProcessLogger(_ => ())
            }

            if (config.dieIfTaskTakesLongerThan.exists(_ < duration.toMillis)) {
              throw new Exception("This took too long: "+duration)
            }

            invocable -> duration
      })
    }
  }

  println(s"\n...benchmark finished.")
}
