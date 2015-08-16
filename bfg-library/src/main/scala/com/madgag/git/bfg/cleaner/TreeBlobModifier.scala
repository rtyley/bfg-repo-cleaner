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

import com.madgag.git.bfg.MemoUtil
import com.madgag.git.bfg.model.{TreeBlobEntry, _}
import org.eclipse.jgit.lib.ObjectId

trait TreeBlobModifier extends Cleaner[TreeBlobs] {

  val memoisedCleaner: Cleaner[TreeBlobEntry] = MemoUtil.concurrentCleanerMemo[TreeBlobEntry](Set.empty) {
    entry =>
      val (mode, objectId) = fix(entry)
      TreeBlobEntry(entry.filename, mode, objectId)
  }

  def fix(entry: TreeBlobEntry): (BlobFileMode, ObjectId) // implementing code can not safely know valid filename

  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.map(memoisedCleaner)

}
