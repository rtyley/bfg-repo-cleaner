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
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.scalatest.{FlatSpec, Inspectors, Matchers, OptionValues}
import org.eclipse.jgit.lib.FileMode.{REGULAR_FILE,EXECUTABLE_FILE}

import scalax.file.ImplicitConversions._
import scalax.file.Path

class MainSpec extends FlatSpec with Matchers with OptionValues with Inspectors {

  // concurrent testing against scala.App is not safe https://twitter.com/rtyley/status/340376844916387840

  "CLI" should "not change commits unnecessarily" in new unpackedRepo("/sample-repos/exampleWithInitialCleanHistory.git.zip") {
    implicit val r = reader

    ensureInvariantValue(commitHist() take 2) {
      ensureRemovalFrom(commitHist()).ofCommitsThat(haveCommitWhereObjectIds(contain(abbrId("294f")))) {
        run("--strip-blobs-bigger-than 1K")
      }
    }
  }


  "removing empty trees" should "work" in new unpackedRepo("/sample-repos/folder-example.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(haveFolder("secret-files")) {
      run("--delete-files {credentials,passwords}.txt")
    }
  }

  "removing big blobs" should "definitely still remove blobs even if they have identical size" in new unpackedRepo("/sample-repos/moreThanOneBigBlobWithTheSameSize.git.zip") {
    ensureRemovalOfBadEggs(packedBlobsOfSize(1024), (contain allElementsOf Set(abbrId("06d7"), abbrId("cb2c"))).matcher[Traversable[ObjectId]]) {
      run("--strip-blobs-bigger-than 512B")
    }
  }

  "converting to Git LFS" should "create a file in lfs/objects" in new unpackedRepo("/sample-repos/repoWithBigBlobs.git.zip") {
    ensureRemovalOfBadEggs(packedBlobsOfSize(11238), (contain only abbrId("596c")).matcher[Traversable[ObjectId]]) {
      run("--convert-to-git-lfs *.png --no-blob-protection")
    }
    val lfsFile = repo.getDirectory / "lfs" / "objects" / "e0" / "eb" / "e0ebd49837a1cced34b9e7d3ff2fa68a8100df8f158f165ce139e366a941ba6e"

    lfsFile.size.value shouldBe 11238
  }

  "removing a folder named '.git'" should "work" in new unpackedRepo("/sample-repos/badRepoContainingDotGitFolder.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(haveFolder(".git")) {
      run("--delete-folders .git --no-blob-protection")
    }
  }

  "cleaning" should "not crash encountering a protected an annotated tag" in new unpackedRepo("/sample-repos/annotatedTagExample.git.zip") {
    ensureInvariantCondition(haveRef("chapter1", haveFile("chapter1.txt"))) {
      ensureRemovalFrom(commitHist("master")).ofCommitsThat(haveFile("chapter2.txt")) {
        run("--strip-blobs-bigger-than 10B --protect-blobs-from chapter1")
      }
    }
  }

  "cleaning" should "not crash encountering a protected branch containing a slash in it's name" in new unpackedRepo("/sample-repos/branchNameWithASlash.git.zip") {
    ensureInvariantCondition(haveRef("feature/slashes-are-ugly", haveFile("bar"))) {
      ensureRemovalFrom(commitHist("master")).ofCommitsThat(haveFile("bar")) {
        run("--delete-files bar --protect-blobs-from feature/slashes-are-ugly")
      }
    }
  }

  "strip blobs by id" should "work" in new unpackedRepo("/sample-repos/example.git.zip") {
    implicit val r = reader

    val badBlobs = Set(abbrId("db59"), abbrId("86f9"))
    val blobIdsFile = Path.createTempFile()
    blobIdsFile.writeStrings(badBlobs.map(_.name()), "\n")

    ensureRemovalFrom(commitHist()).ofCommitsThat(haveCommitWhereObjectIds(contain(abbrId("db59")))) {
      run(s"--strip-blobs-with-ids ${blobIdsFile.path}")
    }
  }

  "deleting a folder" should "not crash encountering a submodule" in new unpackedRepo("/sample-repos/usedToHaveASubmodule.git.zip") {
    ensureInvariantCondition(haveRef("master", haveFile("alpha"))) {
      ensureRemovalFrom(commitHist()).ofCommitsThat(haveFolder("shared")) {
        run("--delete-folders shared")
      }
    }
  }

  "deleting" should "not crash encountering a protected submodule" in new unpackedRepo("/sample-repos/unwantedSubmodule.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(haveFile("foo.txt")) {
      run("--delete-folders bar --delete-files foo.txt")
    }
  }

  "deleting" should "not crash on encountering a commit with bad encoding header" in new unpackedRepo("/sample-repos/badEncoding.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(haveFile("test.txt")) {
      run("--no-blob-protection --delete-files test.txt")
    }
  }

  "Corrupt trees containing duplicate filenames" should "be cleaned by removing the file with the duplicate FileName, leaving the folder" in new unpackedRepo("/sample-repos/corruptTreeDupFileName.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(haveFile("2.0.0")) {
      run("--fix-filename-duplicates-preferring tree")
    }
  }

  "Corrupt trees containing bad file modes" should "make .pl and .sh files executable and all other files non-executable" in new unpackedRepo("/sample-repos/chmodExample.git.zip") {
    ensureRemovalFrom(commitHist()).ofCommitsThat(
      haveFileMode("test.pl", REGULAR_FILE) or haveFileMode("test.sh", REGULAR_FILE) or haveFileMode("test.txt", EXECUTABLE_FILE)
    ) {
      run("--no-blob-protection --chmod:-x=*.* --chmod:+x=*.{sh,pl}")
    }
  }
}

