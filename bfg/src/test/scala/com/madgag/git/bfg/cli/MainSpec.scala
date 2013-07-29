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
import org.specs2.matcher.{MustThrownMatchers, Matcher}
import scalax.file.Path
import com.madgag.git._
import com.madgag.git.test._
import org.specs2.specification.Scope

class MainSpec extends Specification {

  sequential // concurrent testing against scala.App is not safe https://twitter.com/rtyley/status/340376844916387840

  "CLI" should {
    "not change commits unnecessarily" in new unpackedRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip") {
      implicit val r = reader

      ensureInvariant(commitHist take 2) {
        ensureRemovalOf(commitHistory(haveCommitWhereObjectIds(contain(be_==(abbrId("294f")))).atLeastOnce)) {
          run("--strip-blobs-bigger-than 1K")
        }
      }
    }

    "remove empty trees" in new unpackedRepo("/sample-repos/folder-example.git.zip") {
      ensureRemovalOf(commitHistory(haveFolder("secret-files").atLeastOnce)) {
        run("--delete-files {credentials,passwords}.txt")
      }
    }

    "remove bad folder named '.git'" in new unpackedRepo("/sample-repos/badRepoContainingDotGitFolder.git.zip") {
      ensureRemovalOf(commitHistory(haveFolder(".git").atLeastOnce)) {
        run("--delete-folders .git --no-blob-protection")
      }
    }

    "strip blobs by id" in new unpackedRepo("/sample-repos/example.git.zip") {
      implicit val r = reader

      val badBlobs = Set(abbrId("db59"),abbrId("86f9"))
      val blobIdsFile = Path.createTempFile()
      blobIdsFile.writeStrings(badBlobs.map(_.name()),"\n")

      ensureRemovalOf(commitHistory(haveCommitWhereObjectIds(contain(be_==(abbrId("db59")))).atLeastOnce)) {
        run(s"--strip-blobs-with-ids ${blobIdsFile.path}")
      }
    }
  }

  "Massive commit messages" should {
    "be handled without crash (ie LargeObjectException) if the user specifies that the repo contains massive non-file objects" in
      new unpackedRepo("/sample-repos/huge10MBCommitMessage.git.zip") {

      ensureRemovalOf(haveRef("master", be_===(abbrId("d887")))) {
        run("--strip-blobs-bigger-than 1K --repo-contains-massive-non-file-objects 20M")
      }
    }
  }
}

class unpackedRepo(filePath: String) extends Scope with MustThrownMatchers {

  implicit val repo = unpackRepo(filePath)
  implicit lazy val (revWalk, reader) = repo.singleThreadedReaderTuple

  def haveFolder(name: String): Matcher[RevCommit] = be_==(name).atLeastOnce ^^ {
    (c: RevCommit) => c.getTree.walk(postOrderTraversal = true).withFilter(_.isSubtree).map(_.getNameString).toList
  }

  def run(options: String) {
    Main.main(options.split(' ') :+ repo.getDirectory.getAbsolutePath)
  }

  def commitHist(implicit repo: Repository) = repo.git.log.all.call.toSeq.reverse

  def haveCommitWhereObjectIds(boom: Matcher[Traversable[ObjectId]])(implicit reader: ObjectReader): Matcher[RevCommit] = boom ^^ {
    (c: RevCommit) => c.getTree.walk().map(_.getObjectId(0)).toSeq
  }

  def haveRef(refName: String, objectIdMatcher: Matcher[ObjectId]): Matcher[Repository] = objectIdMatcher ^^ {
    (r: Repository) => r resolve (refName) aka s"Ref [$refName]"
  }

  def commitHistory(histMatcher: Matcher[Seq[RevCommit]]): Matcher[Repository] = histMatcher ^^ {
    (r: Repository) => commitHist(r)
  }

  def ensureRemovalOf[T](dirtMatchers: Matcher[Repository]*)(block: => T) = {
    // repo.git.gc.call() ??
    repo must (dirtMatchers.reduce(_ and _))
    block
    // repo.git.gc.call() ??
    repo must dirtMatchers.map(not(_)).reduce(_ and _)
  }

  def ensureInvariant[T, S](f: => S)(block: => T) = {
    val originalValue = f
    block
    f mustEqual originalValue
  }
}