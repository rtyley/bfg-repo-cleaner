/*
 * Copyright (c) 2015 Roberto Tyley
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

package com.madgag.git

import com.madgag.git.LFS.Pointer
import com.madgag.git.test._
import org.eclipse.jgit.lib.Constants._
import org.eclipse.jgit.lib.ObjectInserter
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.nio.file.Files.createTempFile

class LFSSpec extends AnyFlatSpec with Matchers with OptionValues {
  "Our implementation of Git LFS Pointers" should "create pointers that have the same Git id as the ones produced by `git lfs pointer`" in {
    val pointer = LFS.Pointer("b2893eddd9b394bfb7efadafda2ae0be02c573fdd83a70f26c781a943f3b7016", 21616)

    val pointerObjectId = new ObjectInserter.Formatter().idFor(OBJ_BLOB, pointer.bytes)

    pointerObjectId shouldBe "1d90744cffd9e9f324870ed60b6d1258e56a39e1".asObjectId
  }

  it should "have the correctly sharded path" in {
    val pointer = LFS.Pointer("b2893eddd9b394bfb7efadafda2ae0be02c573fdd83a70f26c781a943f3b7016", 21616)

    pointer.path shouldBe Seq("b2", "89", "b2893eddd9b394bfb7efadafda2ae0be02c573fdd83a70f26c781a943f3b7016")
  }

  it should "calculate pointers correctly directly from the Git database, creating a temporary file" in {
    implicit val repo = unpackRepo("/sample-repos/example.git.zip")
    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    val tmpFile = createTempFile(s"bfg.test.git-lfs",".conv")

    val pointer = LFS.pointerFor(abbrId("06d7").open, tmpFile)

    pointer shouldBe Pointer("5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef", 1024)

    Files.size(tmpFile) shouldBe 1024
  }
}