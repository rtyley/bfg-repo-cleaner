package lib

import java.lang.System._
import java.util.concurrent.TimeUnit._

import scala.concurrent.duration.{Duration, FiniteDuration}

object Timing {

  def measureTask[T](description: String)(block: => T): Duration = {
    val start = nanoTime
    val result = block
    val duration = FiniteDuration(nanoTime - start, NANOSECONDS)
    println(s"$description completed in %,d ms.".format(duration.toMillis))
    duration
  }
}
