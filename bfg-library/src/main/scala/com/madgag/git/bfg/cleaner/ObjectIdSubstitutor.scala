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
import com.madgag.git.bfg.cleaner.ObjectIdSubstitutor._
import com.madgag.git._
import com.madgag.git.bfg.model.{HasMessage, TagNode, CommitNode}

class CommitMessageObjectIdsUpdater(val objectIdSubstitutor: ObjectIdSubstitutor) extends CommitNodeCleaner with MessageObjectIdsUpdater[CommitNode, CommitNodeCleanerKit]

class TagMessageObjectIdsUpdater(val objectIdSubstitutor: ObjectIdSubstitutor) extends TagNodeCleaner with MessageObjectIdsUpdater[TagNode, TagNodeCleanerKit]

trait MessageObjectIdsUpdater[N <: HasMessage[N], K <: NodeMessageCleanerKit] {
  val objectIdSubstitutor: ObjectIdSubstitutor

  def fixer(kit: K): Cleaner[N] = _.updateMessageWith(message => objectIdSubstitutor.replaceOldIds(message, kit.threadLocalResources.reader(), kit.mapper))
}

object ObjectIdSubstitutor {

  object OldIdsPrivate extends ObjectIdSubstitutor {
    def format(oldIdText: String, newIdText: String) = newIdText
  }

  object OldIdsPublic extends ObjectIdSubstitutor {
    def format(oldIdText: String, newIdText: String) = s"$newIdText [formerly $oldIdText]"
  }

  val hexRegex = """\b\p{XDigit}{10,40}\b""".r // choose minimum size based on size of project??

}

trait ObjectIdSubstitutor {

  def format(oldIdText: String, newIdText: String): String

  // slow!
  def replaceOldIds(message: String, reader: ObjectReader, mapper: Cleaner[ObjectId]): String = {
    hexRegex.replaceAllIn(message, m => {
      Some(AbbreviatedObjectId.fromString(m.matched)).flatMap(id=> reader.resolveExistingUniqueId(id).toOption).flatMap(mapper.substitution).map {
        case (oldId, newId) =>
          format(m.matched, reader.abbreviate(newId, m.matched.length).name)
      }.getOrElse(m.matched)
    })
  }
}