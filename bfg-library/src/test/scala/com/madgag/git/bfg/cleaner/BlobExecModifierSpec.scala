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

package com.madgag.git.bfg.cleaner

import com.madgag.git.bfg.model.{FileName, Tree}
import com.madgag.git.test.unpackRepo
import org.specs2.mutable._
import com.madgag.git._
import com.madgag.git.bfg.model.{TreeBlobs, BlobFileMode, FileName, Tree}
import com.madgag.git.test._

class BlobExecModifierSpec extends Specification {
  "BlobExecModifier" should {
    "successfully run" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      println(repo.getDirectory.getAbsolutePath)

      val oldTreeBlobs = Tree(repo.resolve("master^{tree}:folder")).blobs

      val cleaner = new BlobExecModifier {
        override def command = "echo 'cat'"

        override val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources =
          repo.getObjectDatabase.threadLocalResources
      }
      val newTreeBlobs = cleaner(oldTreeBlobs)

      val diff = oldTreeBlobs.diff(newTreeBlobs)

      diff.changed must contain(FileName("hero"), FileName("zero"))
    }
  }
}
