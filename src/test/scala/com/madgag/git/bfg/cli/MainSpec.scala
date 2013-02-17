/*
 * Copyright (c) 2012, 2013 Roberto Tyley
 *
 * This file is part of 'BFG Repo-Cleaner' - a tool for removing large
 * or troublesome blobs from Git repositories.
 *
 * BFG Repo-Cleaner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BFG Repo-Cleaner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/ .
 */

package com.madgag.git.bfg.cli

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.madgag.git.bfg._
import com.madgag.git.bfg.GitUtil._
import scala.collection.convert.wrapAsScala._

class MainSpec extends FlatSpec with ShouldMatchers {
  "CLI" should "not change commits unnecessarily" in {
    implicit val repo = unpackRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip")
    implicit val reader = repo.newObjectReader
    def commitHist = repo.git.log.all.call.toSeq.reverse

    val cleanStartCommits = Seq("ee1b29", "b14312").map(abbrId)

    commitHist take 2 should equal(cleanStartCommits)
    repo resolve ("master") should be(abbrId("a9b7f0"))

    Main.main("--strip-blobs-bigger-than 1K".split(' ') :+ repo.getDirectory.getAbsolutePath)

    commitHist take 2 should equal(cleanStartCommits)
    repo resolve ("master") should not be (abbrId("a9b7f0"))
  }
}
