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

import java.util.Date

import com.madgag.git.bfg.cleaner.CommitNodeCleaner.Kit
import com.madgag.git.bfg.model.CommitNode
import org.eclipse.jgit.lib.PersonIdent

class CommitTimestampCurfewShifter(curfew: Date => Boolean) extends CommitNodeCleaner {
  override def fixer(kit: Kit): Cleaner[CommitNode] = { commitNode =>
    // TODO: implement
    commitNode
  }

  private def changeTimestamps(commit: CommitNode, authorWhen: Date, committerWhen: Date): CommitNode = commit.copy(
    author = new PersonIdent(commit.author, authorWhen),
    committer = new PersonIdent(commit.committer, committerWhen)
  )
}
