/*
 * Copyright (c) 2012 Roberto Tyley
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

package com.madgag.git.bfg

import com.madgag.git._
import com.madgag.git.test._
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GitUtilSpec extends AnyFlatSpec with Matchers {
  implicit val repo: FileRepository = unpackRepo("/sample-repos/example.git.zip")

  "reachable blobs" should "match expectations" in {
    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    allBlobsReachableFrom(abbrId("475d") asRevCommit) shouldBe Set("d8d1", "34bd", "e69d", "c784", "d004").map(abbrId)
  }
}