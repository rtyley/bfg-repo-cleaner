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

import org.eclipse.jgit.revwalk.RevWalk
import com.madgag.git.bfg.cleaner.ObjectIdCleaner
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffEntry.ChangeType._
import com.madgag.text.{ByteSize, Text}
import Text._
import scala.Some
import scala.collection.convert.wrapAsScala._
import com.madgag.git._

object Reporter {
  def reportProtectedCommitsAndTheirDirt(reports: List[ProtectedObjectDirtReport], objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit revWalk: RevWalk) {
    reports.foreach {
      report =>
        implicit val reader = revWalk.getObjectReader

        val objectTitle = s" * ${report.revObject.typeString} ${report.revObject.shortName} (protected by '${objectIdCleanerConfig.objectProtection.objectProtection(report.revObject).mkString("', '")}')"

        report.replacementTreeOrBlob match {
          case None => println(objectTitle)
          case Some(newId) =>
            val tw = new TreeWalk(reader)
            tw.setRecursive(true)
            tw.reset

            tw.addTree(report.originalTreeOrBlob.asRevTree)
            tw.addTree(newId.asRevTree)
            tw.setFilter(TreeFilter.ANY_DIFF)
            val diffEntries = DiffEntry.scan(tw).filterNot(_.getChangeType == ADD).map(d => d.getOldPath + " (" + ByteSize.format(d.getOldId.toObjectId.open.getSize) + ")")

            if (diffEntries.isEmpty) {
              println(objectTitle + " - dirty")
            } else {
              println(objectTitle + " - contains " + plural(diffEntries, "dirty file") + " : ")
              abbreviate(diffEntries, "...").foreach {
                dirtyFile => println("\t- " + dirtyFile)
              }
            }
        }
    }

    if (reports.exists(_.dirtProtector)) {
      println("\nWARNING: This protected content may be removed from *older* commits, but as\n" +
        "your current commits still use it, it will STILL exist in your repository.\n\n" +
        "If you *really* want this content gone, make a manual commit that removes it,\n" +
        "and then run the BFG on a fresh copy of your repo.")
      // TODO would like to abort here if we are cleaning 'private' data.
    }
  }

}
