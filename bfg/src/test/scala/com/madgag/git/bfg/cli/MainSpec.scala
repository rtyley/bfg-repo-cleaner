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

import org.specs2.mutable._
import scalax.file.Path
import com.madgag.git._
import bfg.cli.test.unpackedRepo

class MainSpec extends Specification {

  "CLI" should {
    "not change commits unnecessarily" in new unpackedRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip") {
      implicit val r = reader

      ensureInvariant(commitHist() take 2) {
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

    "remove all big blobs, even if they have identical size" in new unpackedRepo("/sample-repos/moreThanOneBigBlobWithTheSameSize.git.zip") {
      ensureRemovalOfBadEggs(packedBlobsOfSize(1024), contain(allOf(abbrId("06d7"), abbrId("cb2c")))) {
        run("--strip-blobs-bigger-than 512B")
      }
    }

    "remove bad folder named '.git'" in new unpackedRepo("/sample-repos/badRepoContainingDotGitFolder.git.zip") {
      ensureRemovalOf(commitHistory(haveFolder(".git").atLeastOnce)) {
        run("--delete-folders .git --no-blob-protection")
      }
    }

    "not crash when protecting an annotated tag" in new unpackedRepo("/sample-repos/annotatedTagExample.git.zip") {
      ensureInvariant(haveRef("chapter1", haveFile("chapter1.txt"))) {
        ensureRemovalOf(commitHistoryFor("master")(haveFile("chapter2.txt").atLeastOnce)) {
          run("--strip-blobs-bigger-than 10B --protect-blobs-from chapter1")
        }
      }
    }

    "not crash when facing a protected branch containing a slash in it's name" in new unpackedRepo("/sample-repos/branchNameWithASlash.git.zip") {
      ensureInvariant(haveRef("feature/slashes-are-ugly", haveFile("bar"))) {
        ensureRemovalOf(commitHistoryFor("master")(haveFile("bar").atLeastOnce)) {
          run("--delete-files bar --protect-blobs-from feature/slashes-are-ugly")
        }
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

    "not crash on encountering protected submodule" in new unpackedRepo("/sample-repos/unwantedSubmodule.git.zip") {
      ensureRemovalOf(commitHistory(haveFile("foo.txt").atLeastOnce)) {
        run("--delete-folders bar --delete-files foo.txt")
      }
    }
  }

  "Corrupt trees containing duplicate filenames" should {
    "be cleaned by removing the file with the duplicate FileName, leaving the folder" in new unpackedRepo("/sample-repos/corruptTreeDupFileName.git.zip") {
      ensureRemovalOf(commitHistory(haveFile("2.0.0").atLeastOnce)) {
        run("--fix-filename-duplicates-preferring tree")
      }
    }
  }
}

