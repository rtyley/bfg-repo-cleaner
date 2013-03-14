package com.madgag.git.bfg.cli

import com.madgag.git.bfg.model.FileName
import org.specs2.mutable._

class CLIConfigSpecs extends Specification {
  "CLI config" should {
    "understand lone include" in {
      val predicate = parse("-fi *.txt")
      predicate(FileName("panda")) should beFalse
      predicate(FileName("foo.txt")) should beTrue
      predicate(FileName("foo.java")) should beFalse
    }

    "understand lone exclude" in {
      val predicate = parse("-fe *.txt")
      predicate(FileName("panda")) should beTrue
      predicate(FileName("foo.txt")) should beFalse
      predicate(FileName("foo.java")) should beTrue
    }

    "understand include followed by exclude" in {
      val predicate = parse("-fi *.txt -fe Poison.*")
      predicate(FileName("panda")) should beFalse
      predicate(FileName("foo.txt")) should beTrue
      predicate(FileName("foo.java")) should beFalse
      predicate(FileName("Poison.txt")) should beFalse
    }

    "understand exclude followed by include" in {
      val predicate = parse("-fe *.xml -fi hbm.xml")
      predicate(FileName("panda")) should beTrue
      predicate(FileName("foo.xml")) should beFalse
      predicate(FileName("hbm.xml")) should beTrue
    }

    def parse(args: String) = CLIConfig.parser.parse(args.split(' ') :+ "my-repo.git", CLIConfig()).get.filterContentPredicate
  }
}
