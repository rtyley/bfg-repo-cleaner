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

import com.madgag.git._
import com.madgag.git.bfg.cli.test.unpackedRepo
import org.eclipse.jgit.revwalk.{RevWalk, RevCommit}
import org.eclipse.jgit.lib.ObjectReader
import com.madgag.git.bfg.model.Commit
import org.scalatest.{FlatSpec, Matchers}

class PruneEmptyCommitsSpec extends FlatSpec with Matchers {

  // concurrent testing against scala.App is not safe https://twitter.com/rtyley/status/340376844916387840

  def onlyTouchesPath(c: RevCommit, predicate: String => Boolean)(implicit revWalk: RevWalk, objectReader: ObjectReader): Boolean = if (c.getParentCount==0) {
    c.getTree.walk().exists(tw => predicate(tw.getPathString))
  } else {
    c.getParents.forall(p => diff(c.getTree, p.getTree).forall(de => predicate(de.getOldPath) || predicate(de.getNewPath)))
  }

  "CLI" should "not remove empty commits by default" in
      new unpackedRepo("/sample-repos/aRepoProneToEmptyCommitsOnCleaning.git.zip") {
      ensureInvariantValue(commitHist("HEAD").size) {
        ensureRemovalFrom(commitHist()).ofCommitsThat(haveFile("foo")) {
          run("--delete-files foo --no-blob-protection")
        }
      }
    }

    "CLI" should "remove empty commits if prune flag set" in new unpackedRepo("/sample-repos/aRepoProneToEmptyCommitsOnCleaning.git.zip") {
      val (commitsThatOnlyTouchFoo, commitsThatTouchNonFooFiles) = commitHist().partition(c => onlyTouchesPath(c, _.endsWith("foo")))

      ensureRemovalFrom(commitHist()).ofCommitsThat(haveFile("foo")) {
        run("--delete-files foo --no-blob-protection --private --prune-empty-commits")
      }

      def commitAuthor(a: RevCommit) = Commit.apply(a).node.author

      commitHist().map(commitAuthor) should equal (commitsThatTouchNonFooFiles.map(commitAuthor))
    }
}

