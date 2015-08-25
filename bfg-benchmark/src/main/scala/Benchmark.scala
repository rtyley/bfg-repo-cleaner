import lib.Timing.measureTask
import lib._
import model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.sys.process._
import scalax.file.PathMatcher.IsDirectory
import scalax.io.Codec

/*
 * Vary BFG runs by:
 * Java version
 * BFG version (JGit version?)
 *
 */
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

      val tasksFuture = for {
        bfgInvocableEngineSet <- bfgInvocableEngineSet(config)
      } yield {
        val gfbInvocableEngineSetOpt =
          if (config.onlyBfg) None else Some(InvocableEngineSet[GFBInvocation](GitFilterBranch, Seq(InvocableGitFilterBranch)))
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
      repoSpecDir <- config.repoSpecDirs.toList
      availableCommandDirs = (repoSpecDir / "commands").children().filter(IsDirectory).toList
      // println(s"Available commands for $repoName : ${availableCommandDirs.map(_.name).mkString(", ")}")
      commandDir <- availableCommandDirs.filter(p => config.commands(p.name))
    } yield {

      val repoName = repoSpecDir.name

      val commandName = commandDir.name
      
      commandName -> (for {
        invocableEngineSet <- invocableEngineSets
      } yield for {
          (invocable, processMaker) <- invocableEngineSet.invocationsFor(commandDir)
        } yield {
        val cleanRepoDir = repoExtractor.extractRepoFrom(repoSpecDir / "repo.git.zip")
        commandDir.children().foreach(p => p.copyTo(cleanRepoDir / p.name))
        val process = processMaker(cleanRepoDir)

            val duration = measureTask(s"$commandName - $invocable") {
              process ! ProcessLogger(_ => Unit)
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
