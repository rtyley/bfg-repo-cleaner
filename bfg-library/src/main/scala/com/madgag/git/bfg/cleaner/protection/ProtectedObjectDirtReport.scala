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

package com.madgag.git.bfg.cleaner.protection

import org.eclipse.jgit.revwalk.{RevWalk, RevObject}
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import com.madgag.git._
import scala.collection.convert.wrapAsScala._
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffEntry.ChangeType._

/**
 * @param revObject - the protected object (eg protected because it is the HEAD commit)
 * @param originalTreeOrBlob - the unmodified content-object referred to by the protected object (may be same object)
 * @param replacementTreeOrBlob - an option, populated if cleaning creates a replacement for the content-object
 */
case class ProtectedObjectDirtReport(revObject: RevObject, originalTreeOrBlob: RevObject, replacementTreeOrBlob: Option[ObjectId]) {
  val objectProtectsDirt: Boolean = replacementTreeOrBlob.isDefined

  def dirt(implicit revWalk: RevWalk): Option[Seq[DiffEntry]] = replacementTreeOrBlob.map { newId =>
    val tw = new TreeWalk(revWalk.getObjectReader)
    tw.setRecursive(true)
    tw.reset

    tw.addTree(originalTreeOrBlob.asRevTree)
    tw.addTree(newId.asRevTree)
    tw.setFilter(TreeFilter.ANY_DIFF)
    DiffEntry.scan(tw).filterNot(_.getChangeType == ADD)
  }
}
