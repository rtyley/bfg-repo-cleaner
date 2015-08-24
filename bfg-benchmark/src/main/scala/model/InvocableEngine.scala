package model

import scala.sys.process.{Process, ProcessBuilder}
import scalax.file.ImplicitConversions._
import scalax.file.Path
import scalax.file.defaultfs.DefaultPath
import scalax.io.Input

trait EngineInvocation

case class BFGInvocation(args: String) extends EngineInvocation

case class GFBInvocation(args: Seq[String]) extends EngineInvocation


trait InvocableEngine[InvocationArgs <: EngineInvocation] {

    def processFor(invocation: InvocationArgs)(repoPath: DefaultPath): ProcessBuilder
}

case class InvocableBFG(java: Java, bfgJar: BFGJar) extends InvocableEngine[BFGInvocation] {

  def processFor(invocation: BFGInvocation)(repoPath: DefaultPath) =
    Process(s"${java.javaCmd} -jar ${bfgJar.path.path} ${invocation.args}", repoPath)

}

object InvocableGitFilterBranch extends InvocableEngine[GFBInvocation] {

  def processFor(invocation: GFBInvocation)(repoPath: DefaultPath) =
    Process(Seq("git", "filter-branch") ++ invocation.args, repoPath)
}

/*
We want to allow the user to vary:
 - BFGs (jars, javas)
 - Tasks (delete a file, replace text) in [selection of repos]

 Tasks will have a variety of different invocations for different engines
 */

trait EngineType[InvocationType <: EngineInvocation] {
  val configName: String

  def argsFor(config: Input): InvocationType

  def argsOptsFor(commandDir: Path): Option[InvocationType] = {
    val paramsPath = commandDir / s"$configName.txt"
    if (paramsPath.exists) Some(argsFor(paramsPath)) else None
  }
}

case object BFG extends EngineType[BFGInvocation] {

  val configName = "bfg"

  def argsFor(config: Input) = BFGInvocation(config.string)
}

case object GitFilterBranch extends EngineType[GFBInvocation] {

  val configName = "gfb"

  def argsFor(config: Input) = GFBInvocation(config.lines().toSeq)
}
