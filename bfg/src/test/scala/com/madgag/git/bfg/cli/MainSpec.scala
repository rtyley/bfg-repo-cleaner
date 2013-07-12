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
      repo resolve ("master") mustEqual abbrId("a9b7f0")

      run("--strip-blobs-bigger-than 1K")

      commitHist take 2 mustEqual cleanStartCommits
      repo resolve ("master") mustNotEqual abbrId("a9b7f0")
    }

    "remove empty trees" in {
      implicit val repo = unpackRepo("/sample-repos/folder-example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      commitHist must haveFolder("secret-files").atLeastOnce
      repo.resolve("master") mustEqual abbrId("cd1a")

      run("--delete-files {credentials,passwords}.txt")

      repo.resolve("master") mustNotEqual abbrId("cd1a")
      commitHist must (not(haveFolder("secret-files"))).forall
    }

    "remove bad folder named '.git'" in {
      implicit val repo = unpackRepo("/sample-repos/badRepoContainingDotGitFolder.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      commitHist must haveFolder(".git").atLeastOnce

      run("--delete-folders .git --no-blob-protection")

      commitHist must (not(haveFolder(".git"))).forall
    }

    "strip blobs by id" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      def haveCommitWhereObjectIds(boom: Matcher[Traversable[ObjectId]]): Matcher[RevCommit] = boom ^^ {
          (c: RevCommit) => c.getTree.walk().map(_.getObjectId(0)).toList
        }

      val badBlobs = Set(abbrId("db59"),abbrId("86f9"))

      val blobIdsFile = Path.createTempFile()
      blobIdsFile.writeStrings(badBlobs.map(_.name()),"\n")

      commitHist must haveCommitWhereObjectIds(containAllOf(badBlobs.toSeq)).atLeastOnce

      run(s"--strip-blobs-with-ids ${blobIdsFile.path}")

      commitHist must (not(haveCommitWhereObjectIds(containAnyOf(badBlobs.toSeq)))).forall
    }
  }

  "Massive commit messages" should {
    "be handled without crash (ie LargeObjectException) if the user specifies that the repo contains massive non-file objects" in {
      implicit val repo = unpackRepo("/sample-repos/huge10MBCommitMessage.git.zip")
      implicit val reader = repo.newObjectReader

      repo resolve ("master") mustEqual abbrId("d887")
      run("--strip-blobs-bigger-than 1K --repo-contains-massive-non-file-objects 20M")
      repo resolve ("master") mustNotEqual abbrId("d887")
    }
  }

  def run(options: String)(implicit repo: Repository) {
    Main.main(options.split(' ') :+ repo.getDirectory.getAbsolutePath)
  }

  def commitHist(implicit repo: Repository) = repo.git.log.all.call.toSeq.reverse

  def haveFolder(name: String)(implicit reader: ObjectReader): Matcher[RevCommit] = be_===(name).atLeastOnce ^^ {
    (c: RevCommit) => c.getTree.walk(postOrderTraversal = true).withFilter(_.isSubtree).map(_.getNameString).toList
  }
}
