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

import cleaner.{RepoRewriter, BlobRemover}
import org.scalatest._
import matchers.ShouldMatchers
import org.eclipse.jgit.lib.ObjectId
import GitUtil._
import GitTestHelper._
import org.eclipse.jgit.api.Git
import scala.collection.JavaConversions._

class RepoRewriteSpec extends FlatSpec with ShouldMatchers {

  "Git repo" should "not explode" in {
    implicit val repo = unpackRepo("/sample-repos/example.git.zip")
    val blobsToRemove = Set(ObjectId.fromString("06d7405020018ddf3cacee90fd4af10487da3d20"))
    RepoRewriter.rewrite(repo, new BlobRemover(blobsToRemove))

    val allCommits = new Git(repo).log.all.call.toSeq

    val unwantedBlobsByCommit = allCommits.flatMap(commit => {
      val unwantedBlobs = allBlobsReachableFrom(commit).intersect(blobsToRemove).map(_.shortName)
      if (!unwantedBlobs.isEmpty) Some(commit.shortName -> unwantedBlobs) else None
    }).toMap

    unwantedBlobsByCommit should be('empty)

    allCommits.head.getFullMessage should include("Former-commit-id")
  }
}



