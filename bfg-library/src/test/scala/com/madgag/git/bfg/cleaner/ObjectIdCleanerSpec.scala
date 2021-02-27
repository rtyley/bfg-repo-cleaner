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
import com.madgag.textmatching.Literal
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class ObjectIdCleanerSpec extends AnyFlatSpec with Matchers {
  
  "cleaning" should "not have a StackOverflowError cleaning a repo with deep history" ignore new unpackedRepo("/sample-repos/deep-history.zip") {
    val dirtyCommitWithDeepHistory = "d88ac4f99511667fc0617ea026f3a0ce8a25fd07".asObjectId

    val config = ObjectIdCleaner.Config(
      ProtectedObjectCensus.None,
      treeBlobsCleaners = Seq(new FileDeleter(Literal("foo")))
    )

    ensureCleanerWith(config).removesDirtOfCommitsThat(haveFile("foo")).whenCleaning(dirtyCommitWithDeepHistory)
  }

}

class unpackedRepo(filePath: String) extends bfg.test.unpackedRepo(filePath) {

  class EnsureCleanerWith(config: ObjectIdCleaner.Config) {

    class RemoveDirtOfCommitsThat(commitM: Matcher[RevCommit]) extends Inspectors with Matchers {
      def histOf(c: ObjectId) = repo.git.log.add(c).call.asScala.toSeq.reverse

      def whenCleaning(oldCommit: ObjectId): Unit = {
        val cleaner = new ObjectIdCleaner(config, repo.getObjectDatabase, revWalk)
        forAtLeast(1, histOf(oldCommit)) { commit =>
          commit should commitM
        }

        val cleanCommit = cleaner.cleanCommit(oldCommit)

        forAll(histOf(cleanCommit)) { commit =>
          commit shouldNot commitM
        }
      }
    }

    def removesDirtOfCommitsThat[T](commitM: Matcher[RevCommit]) = new RemoveDirtOfCommitsThat(commitM)
  }

  def ensureCleanerWith(config: ObjectIdCleaner.Config) = new EnsureCleanerWith(config)
}

