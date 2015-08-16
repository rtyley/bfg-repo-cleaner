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

import com.madgag.git.ThreadLocalObjectDatabaseResources
import com.madgag.git.bfg.model._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.RevCommit

object CommitNodeCleaner {

  class Kit(val threadLocalResources: ThreadLocalObjectDatabaseResources,
            val originalRevCommit: RevCommit,
            val originalCommit: Commit,
            val updatedArcs: CommitArcs,
            val mapper: Cleaner[ObjectId]) {

    val arcsChanged = originalCommit.arcs != updatedArcs

    def commitIsChanged(withThisNode: CommitNode) = arcsChanged || originalCommit.node != withThisNode
  }

  def chain(cleaners: Seq[CommitNodeCleaner]) = new CommitNodeCleaner {
    def fixer(kit: CommitNodeCleaner.Kit) = Function.chain(cleaners.map(_.fixer(kit)))
  }
}

trait CommitNodeCleaner {
  def fixer(kit: CommitNodeCleaner.Kit): Cleaner[CommitNode]
}

object FormerCommitFooter extends CommitNodeCleaner {
  val Key = "Former-commit-id"

  override def fixer(kit: CommitNodeCleaner.Kit) = modifyIf(kit.commitIsChanged) {
    _ add Footer(Key, kit.originalRevCommit.name)
  }

  def modifyIf[A](predicate: A => Boolean)(modifier: A => A): (A => A) = v => if (predicate(v)) modifier(v) else v
}
