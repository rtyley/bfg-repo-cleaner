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

package com.madgag.git.bfg.cleaner

import protection.ProtectedObjectCensus
import com.madgag.git.bfg.model.TreeBlobEntry
import ObjectIdSubstitutor._
import org.specs2.mutable._
import com.madgag.git.test._
import scala.collection.mutable

class TreeBlobModifierSpec extends Specification {

  "TreeBlobModifier" should {
    "only clean a given tree entry once" in {
      class CountingTreeBlobModifier extends TreeBlobModifier {
        val counts = mutable.Map[TreeBlobEntry, Int]().withDefaultValue(0)

        def fix(entry: TreeBlobEntry) = {
          counts.synchronized {
            counts(entry) = counts(entry) + 1
          }
          (entry.mode, entry.objectId)
        }
      }

      implicit val repo = unpackRepo("/sample-repos/taleOfTwoBranches.git.zip")

      val countingTreeBlobModifier = new CountingTreeBlobModifier()

      RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ProtectedObjectCensus(Set("HEAD")), treeBlobsCleaners = Seq(countingTreeBlobModifier)))

      countingTreeBlobModifier.counts.values must beEqualTo(1).foreach
    }
  }


}
