package model

import scalax.file.Path
import scalax.file.defaultfs.DefaultPath

case class InvocableEngineSet[InvocationArgs <: EngineInvocation](
  engineType: EngineType[InvocationArgs],
  invocableEngines: Seq[InvocableEngine[InvocationArgs]]
) {

  def invocationsFor(commandDir: Path): Seq[(InvocableEngine[InvocationArgs], DefaultPath => scala.sys.process.ProcessBuilder)] = {
    for {
      args <- engineType.argsOptsFor(commandDir).toSeq
      invocable <- invocableEngines
    } yield (invocable, invocable.processFor(args) _)
  }
}
