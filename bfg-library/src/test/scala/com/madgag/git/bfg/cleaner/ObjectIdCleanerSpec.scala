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

import com.madgag.git._
import com.madgag.git.bfg.cleaner.protection.ProtectedObjectCensus
import com.madgag.git.test._
import com.madgag.textmatching.Literal
import org.eclipse.jgit.revwalk.RevWalk
import org.scalatest.{FlatSpec, Matchers}


class ObjectIdCleanerSpec extends FlatSpec with Matchers {

  "ObjectIdCleaner" should "not have a StackOverflowError cleaning a repo with deep history" in {
      implicit val repo = unpackRepo("/sample-repos/deep-history.zip")
      val revWalk = new RevWalk(repo)

      val config= ObjectIdCleaner.Config(
        ProtectedObjectCensus.None,
        treeBlobsCleaners= Seq(new FileDeleter(Literal("foo")))
      )

      val cleaner = new ObjectIdCleaner(config, repo.getObjectDatabase, revWalk)

      val dirtyCommitWithDeepHistory = "d88ac4f99511667fc0617ea026f3a0ce8a25fd07".asObjectId

      val cleanedCommitId = cleaner(dirtyCommitWithDeepHistory) // we don't want a StackOverflowError here!

      cleanedCommitId shouldNot equal(dirtyCommitWithDeepHistory)
    }
}
