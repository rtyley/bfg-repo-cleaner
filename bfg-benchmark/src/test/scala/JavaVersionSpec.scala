import org.scalatest.{FlatSpec, Matchers, OptionValues}

object JavaVersionSpec extends FlatSpec with OptionValues with Matchers {
  "version" should "parse an example line" in {
    JavaVersion.versionFrom("""java version "1.7.0_51"""").value shouldBe "1.7.0_51"
  }

  it should "parse openjdk weirdness" in {
    JavaVersion.versionFrom("""openjdk version "1.8.0_40-internal"""").value shouldBe "1.8.0_40-internal"
  }
}
