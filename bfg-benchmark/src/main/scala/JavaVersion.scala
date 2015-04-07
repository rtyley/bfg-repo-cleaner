import scala.concurrent._
import scala.sys.process.{ProcessLogger, Process}
import ExecutionContext.Implicits.global

object JavaVersion {
  val VersionRegex = """(?:java|openjdk) version "(.*?)"""".r

  def version(javaCmd: String): Future[String] = {
    val resultPromise = Promise[String]()

    Future {
      val exitCode = Process(s"$javaCmd -version")!ProcessLogger(
        s => for (v <-versionFrom(s)) resultPromise.success(v)
      )
      resultPromise.tryFailure(new IllegalArgumentException(s"$javaCmd exited with code $exitCode, no Java version found"))
    }

    resultPromise.future
  }

  def versionFrom(javaVersionLine: String): Option[String] = {
    VersionRegex.findFirstMatchIn(javaVersionLine).map(_.group(1))
  }

  /*

  ProcessBuilderImpl

      private[this] def lines(
      withInput: Boolean,
      nonZeroException: Boolean,
      log: Option[ProcessLogger]
    ): Stream[String] = {
      val streamed = Streamed[String](nonZeroException)
      val process  = run(BasicIO(withInput, streamed.process, log))

      Spawn(streamed done process.exitValue())
      streamed.stream()
    }
   */
}
