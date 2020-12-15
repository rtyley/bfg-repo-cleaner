package com.madgag.git.bfg.cli

import com.madgag.git.bfg.model.FileName
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CLIConfigSpecs extends AnyFlatSpec with Matchers {


  def parse(args: String) = CLIConfig.parser.parse(args.split(' ') :+ "my-repo.git", CLIConfig()).get.filterContentPredicate

  "CLI config" should "understand lone include" in {
    val predicate = parse("-fi *.txt")
    predicate(FileName("panda")) shouldBe false
    predicate(FileName("foo.txt")) shouldBe true
    predicate(FileName("foo.java")) shouldBe false
  }

  it should "understand lone exclude" in {
    val predicate = parse("-fe *.txt")
    predicate(FileName("panda")) shouldBe true
    predicate(FileName("foo.txt")) shouldBe false
    predicate(FileName("foo.java")) shouldBe true
  }

  it should "understand include followed by exclude" in {
    val predicate = parse("-fi *.txt -fe Poison.*")
    predicate(FileName("panda")) shouldBe false
    predicate(FileName("foo.txt")) shouldBe true
    predicate(FileName("foo.java")) shouldBe false
    predicate(FileName("Poison.txt")) shouldBe false
  }

  it should "understand exclude followed by include" in {
    val predicate = parse("-fe *.xml -fi hbm.xml")
    predicate(FileName("panda")) shouldBe true
    predicate(FileName("foo.xml")) shouldBe false
    predicate(FileName("hbm.xml")) shouldBe true
  }

}
