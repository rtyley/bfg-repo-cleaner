package com.madgag.git.bfg.cli

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.madgag.git.bfg.model.FileName


class CLIConfigSpecs extends FlatSpec with ShouldMatchers {
  "CLI config" should "understand lone include" in {
    val predicate = parse("-fi *.txt")
    predicate(FileName("panda")) should be(false)
    predicate(FileName("foo.txt")) should be(true)
    predicate(FileName("foo.java")) should be(false)
  }

  "CLI config" should "understand lone exclude" in {
    val predicate = parse("-fe *.txt")
    predicate(FileName("panda")) should be(true)
    predicate(FileName("foo.txt")) should be(false)
    predicate(FileName("foo.java")) should be(true)
  }

  "CLI config" should "understand include followed by exclude" in {
    val predicate = parse("-fi *.txt -fe Poison.*")
    predicate(FileName("panda")) should be(false)
    predicate(FileName("foo.txt")) should be(true)
    predicate(FileName("foo.java")) should be(false)
    predicate(FileName("Poison.txt")) should be(false)
  }

  "CLI config" should "understand exclude followed by include" in {
    val predicate = parse("-fe *.xml -fi hbm.xml")
    predicate(FileName("panda")) should be(true)
    predicate(FileName("foo.xml")) should be(false)
    predicate(FileName("hbm.xml")) should be(true)
  }

  def parse(args: String) = CLIConfig.parser.parse(args.split(' ') :+ "my-repo.git", CLIConfig()).get.filterContentPredicate

}
