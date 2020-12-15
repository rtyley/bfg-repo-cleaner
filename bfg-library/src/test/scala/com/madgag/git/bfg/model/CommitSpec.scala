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

package com.madgag.git.bfg.model

import com.madgag.git.bfg.test.unpackedRepo
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommitSpec extends AnyFlatSpec with Matchers with Inspectors {
  "Commit model" should "calculate the same Git commit id for any given commit" in new unpackedRepo("/sample-repos/example.git.zip") {
    forAll (commitHist()) { revCommit =>
      Commit(revCommit).id shouldBe revCommit.toObjectId
    }
  }

}
