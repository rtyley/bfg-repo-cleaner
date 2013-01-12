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

import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId, ObjectReader}
import com.madgag.git.bfg.GitUtil._

object ObjectIdSubstitutor extends CommitCleaner {

  val hexRegex = """\b\p{XDigit}{10,40}\b""".r // choose minimum size based on size of project??

  override def fixer(kit: CommitCleaner.Kit) = cm => cm.copy(message = replaceOldCommitIds(cm.message, kit.objectReader, kit.mapper))

  // slow!
  def replaceOldCommitIds(message: String, reader: ObjectReader, mapper: CleaningMapper[ObjectId]): String = {
    ObjectIdSubstitutor.hexRegex.replaceAllIn(message, m => {
      Some(AbbreviatedObjectId.fromString(m.matched))
        .flatMap(reader.resolveExistingUniqueId).flatMap(mapper.objectIdSubstitution).map {
        case (oldId, newId) =>
          val newName = reader.abbreviate(newId, m.matched.length).name
          s"$newName [formerly $m]"
      }.getOrElse(m.matched)
    })
  }
}
