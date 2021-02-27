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

import com.madgag.git._
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner.ObjectIdCleaner
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffEntry.ChangeType._
import org.eclipse.jgit.lib.{ObjectDatabase, ObjectId}
import org.eclipse.jgit.revwalk.{RevObject, RevWalk}
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter

import scala.jdk.CollectionConverters._

object ProtectedObjectDirtReport {
  def reportsFor(objectIdCleanerConfig: ObjectIdCleaner.Config, objectDB: ObjectDatabase)(implicit revWalk: RevWalk) = {
    val uncaringCleaner: ObjectIdCleaner = new ObjectIdCleaner(
      objectIdCleanerConfig.copy(protectedObjectCensus = ProtectedObjectCensus.None),
      objectDB,
      revWalk
    )

    for (protectedRevObj <- objectIdCleanerConfig.protectedObjectCensus.protectorRevsByObject.keys) yield {
      val originalContentTreeOrBlob = treeOrBlobPointedToBy(protectedRevObj)
      val replacementTreeOrBlob = originalContentTreeOrBlob.fold(uncaringCleaner.cleanBlob.replacement, uncaringCleaner.cleanTree.replacement)
      ProtectedObjectDirtReport(protectedRevObj, originalContentTreeOrBlob.merge, replacementTreeOrBlob)
    }
  }
}

/**
 * The function of the ProtectedObjectDirtReport is tell the user that this is the stuff they've decided
 * to protect in their latest commits - it's the stuff The BFG /would/ remove if you hadn't told it to
 * hold back,
 *
 * @param revObject - the protected object (eg protected because it is the HEAD commit, or even by additional refs)
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
    DiffEntry.scan(tw).asScala.filterNot(_.getChangeType == ADD).toSeq
  }
}
