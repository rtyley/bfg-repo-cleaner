import org.specs2.mutable.Specification
import scala.concurrent.{Future, Promise}
import scala.sys.process.{ProcessLogger, Process}

object JavaVersionSpec extends Specification {
  "version" should {
    "parse an example line" in {
      JavaVersion.versionFrom("""java version "1.7.0_51"""") should beSome("1.7.0_51")
    }
    "parse openjdk weirdness" in {
      JavaVersion.versionFrom("""openjdk version "1.8.0_40-internal"""") should beSome("1.8.0_40-internal")
    }
  }
}
