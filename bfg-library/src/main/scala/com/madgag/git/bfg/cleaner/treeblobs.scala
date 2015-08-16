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

import com.madgag.git.bfg.cleaner.kit.BlobInserter
import com.madgag.git.bfg.model.FileName.ImplicitConversions._
import com.madgag.git.bfg.model.{TreeBlobEntry, _}
import com.madgag.textmatching.TextMatcher
import org.eclipse.jgit.lib.ObjectId

class FileDeleter(fileNameMatcher: TextMatcher) extends Cleaner[TreeBlobs] {
  override def apply(tbs: TreeBlobs) = tbs.entries.filterNot(e => fileNameMatcher(e.filename))
}

class BlobRemover(blobIds: Set[ObjectId]) extends Cleaner[TreeBlobs] {
  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.filter(e => !blobIds.contains(e.objectId))
}

class BlobReplacer(badBlobs: Set[ObjectId], blobInserter: => BlobInserter) extends Cleaner[TreeBlobs] {
  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.map {
    case e if badBlobs.contains(e.objectId) =>
      TreeBlobEntry(FileName(e.filename + ".REMOVED.git-id"), RegularFile, blobInserter.insert(e.objectId.name.getBytes))
    case e => e
  }
}










