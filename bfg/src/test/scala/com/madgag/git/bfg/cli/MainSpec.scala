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

import scala.collection.convert.wrapAsScala._
import org.specs2.mutable._
import org.eclipse.jgit.lib.{ObjectReader, ObjectId, Repository}
import org.eclipse.jgit.revwalk.RevCommit
import org.specs2.matcher.Matcher
import scalax.file.Path
import com.madgag.git._
import com.madgag.git.test._

class MainSpec extends Specification {

  sequential // concurrent testing against scala.App is not safe https://twitter.com/rtyley/status/340376844916387840

  "CLI" should {
    "not change commits unnecessarily" in {
      implicit val repo = unpackRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip")
      implicit val reader = repo.newObjectReader

      val cleanStartCommits = Seq("ee1b29", "b14312").map(abbrId)

      commitHist take 2 mustEqual cleanStartCommits

      ensureRemovalOf(commitHistory(haveCommitWhereObjectIds(contain(be_==(abbrId("294f")))).atLeastOnce)) {
        run("--strip-blobs-bigger-than 1K")
      }

      commitHist take 2 mustEqual cleanStartCommits
    }

    "remove empty trees" in {
      implicit val repo = unpackRepo("/sample-repos/folder-example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      ensureRemovalOf(commitHistory(haveFolder("secret-files").atLeastOnce)) {
        run("--delete-files {credentials,passwords}.txt")
      }
    }

    "remove bad folder named '.git'" in {
      implicit val repo = unpackRepo("/sample-repos/badRepoContainingDotGitFolder.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      ensureRemovalOf(commitHistory(haveFolder(".git").atLeastOnce)) {
        run("--delete-folders .git --no-blob-protection")
      }
    }

    "strip blobs by id" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      val badBlobs = Set(abbrId("db59"),abbrId("86f9"))

      val blobIdsFile = Path.createTempFile()
      blobIdsFile.writeStrings(badBlobs.map(_.name()),"\n")

      ensureRemovalOf(commitHistory(haveCommitWhereObjectIds(contain(be_==(abbrId("db59")))).atLeastOnce)) {
        run(s"--strip-blobs-with-ids ${blobIdsFile.path}")
      }
    }
  }

  "Massive commit messages" should {
    "be handled without crash (ie LargeObjectException) if the user specifies that the repo contains massive non-file objects" in {
      implicit val repo = unpackRepo("/sample-repos/huge10MBCommitMessage.git.zip")
      implicit val reader = repo.newObjectReader

      ensureRemovalOf(haveRef("master", be_===(abbrId("d887")))) {
        run("--strip-blobs-bigger-than 1K --repo-contains-massive-non-file-objects 20M")
      }
    }
  }

  def ensureRemovalOf[T](dirtMatchers: Matcher[Repository]*)(block: => T)(implicit repo: Repository) = {
    repo must (dirtMatchers.reduce(_ and _))
    block
    repo must not(dirtMatchers.reduce(_ or _))
  }

  def run(options: String)(implicit repo: Repository) {
    Main.main(options.split(' ') :+ repo.getDirectory.getAbsolutePath)
  }

  def commitHist(implicit repo: Repository) = repo.git.log.all.call.toSeq.reverse

  def haveCommitWhereObjectIds(boom: Matcher[Traversable[ObjectId]])(implicit reader: ObjectReader): Matcher[RevCommit] = boom ^^ {
    (c: RevCommit) => c.getTree.walk().map(_.getObjectId(0)).toSeq
  }

  def haveFolder(name: String)(implicit reader: ObjectReader): Matcher[RevCommit] = be_===(name).atLeastOnce ^^ {
    (c: RevCommit) => c.getTree.walk(postOrderTraversal = true).withFilter(_.isSubtree).map(_.getNameString).toList
  }

  def haveRef(refName: String, objectIdMatcher: Matcher[ObjectId]): Matcher[Repository] = objectIdMatcher ^^ {
    (r: Repository) => r resolve (refName)
  }

  def commitHistory(histMatcher: Matcher[Seq[RevCommit]]): Matcher[Repository] = histMatcher ^^ {
    (r: Repository) => commitHist(r)
  }
}
