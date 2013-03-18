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

import com.madgag.git.bfg._
import com.madgag.git.bfg.GitUtil._
import scala.collection.convert.wrapAsScala._
import org.specs2.mutable._
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.specs2.matcher.Matcher

class MainSpec extends Specification {
  "CLI" should {
    "not change commits unnecessarily" in {
      implicit val repo = unpackRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip")
      implicit val reader = repo.newObjectReader

      val cleanStartCommits = Seq("ee1b29", "b14312").map(abbrId)

      commitHist take 2 mustEqual cleanStartCommits
      repo resolve ("master") mustEqual abbrId("a9b7f0")

      run("--strip-blobs-bigger-than 1K")

      commitHist take 2 mustEqual cleanStartCommits
      repo resolve ("master") mustNotEqual abbrId("a9b7f0")
    }

    "remove empty trees" in {
      implicit val repo = unpackRepo("/sample-repos/folder-example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      def haveFolder(name: String): Matcher[RevCommit] = be_===(name).atLeastOnce ^^ {
        (c: RevCommit) => c.getTree.walk(postOrderTraversal = true).withFilter(_.isSubtree).map(_.getNameString).toList
      }

      commitHist must haveFolder("secret-files").atLeastOnce

      run("--delete-files {credentials,passwords}.txt")

      commitHist must (not(haveFolder("secret-files"))).forall
    }
  }

  def commitHist(implicit repo: Repository) = repo.git.log.all.call.toSeq.reverse
}
