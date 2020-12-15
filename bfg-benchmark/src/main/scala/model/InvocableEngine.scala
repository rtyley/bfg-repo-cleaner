package model

import com.google.common.io.CharSource
import com.google.common.io.Files.asCharSource

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.StreamConverters._
import scala.sys.process.{Process, ProcessBuilder}

trait EngineInvocation

case class BFGInvocation(args: String) extends EngineInvocation

case class GFBInvocation(args: Seq[String]) extends EngineInvocation


trait InvocableEngine[InvocationArgs <: EngineInvocation] {

    def processFor(invocation: InvocationArgs)(repoPath: File): ProcessBuilder
}

case class InvocableBFG(java: Java, bfgJar: BFGJar) extends InvocableEngine[BFGInvocation] {

  def processFor(invocation: BFGInvocation)(repoPath: File) =
    Process(s"${java.javaCmd} -jar ${bfgJar.path} ${invocation.args}", repoPath)

}

object InvocableGitFilterBranch extends InvocableEngine[GFBInvocation] {

  def processFor(invocation: GFBInvocation)(repoPath: File) =
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

  def argsFor(config: CharSource): InvocationType

  def argsOptsFor(commandDir: Path): Option[InvocationType] = {
    val paramsPath = commandDir.resolve(s"$configName.txt")
    if (Files.exists(paramsPath)) Some(argsFor(asCharSource(paramsPath.toFile, UTF_8))) else None
  }
}

case object BFG extends EngineType[BFGInvocation] {

  val configName = "bfg"

  def argsFor(config: CharSource) = BFGInvocation(config.read())
}

case object GitFilterBranch extends EngineType[GFBInvocation] {

  val configName = "gfb"

  def argsFor(config: CharSource) = GFBInvocation(config.lines().toScala(Seq))
}
