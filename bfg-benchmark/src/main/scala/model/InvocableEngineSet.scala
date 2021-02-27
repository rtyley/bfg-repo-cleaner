package model

import java.io.File
import java.nio.file.Path

case class InvocableEngineSet[InvocationArgs <: EngineInvocation](
  engineType: EngineType[InvocationArgs],
  invocableEngines: Seq[InvocableEngine[InvocationArgs]]
) {

  def invocationsFor(commandDir: Path): Seq[(InvocableEngine[InvocationArgs], File => scala.sys.process.ProcessBuilder)] = {
    for {
      args <- engineType.argsOptsFor(commandDir).toSeq
      invocable <- invocableEngines
    } yield (invocable, invocable.processFor(args) _)
  }
}
