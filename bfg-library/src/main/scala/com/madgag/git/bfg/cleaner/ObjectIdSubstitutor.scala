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
import scala.concurrent._
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

class CommitMessageObjectIdsUpdater(objectIdSubstitutor: ObjectIdSubstitutor) extends CommitNodeCleaner {

  override def fixer(kit: CommitNodeCleaner.Kit) = commitNode => (objectIdSubstitutor.replaceOldIds(commitNode.message, kit.threadLocalResources.reader(), kit.mapper)).map {
    updatedMessage => commitNode.copy(message = updatedMessage)
  }

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
  def replaceOldIds(message: String, reader: ObjectReader, mapper: Cleaner[ObjectId]): Future[String] = {
    val substitutionsFuture: Future[Map[String, String]] = Future.sequence(for {
      m: String <- hexRegex.findAllIn(message).toSet
      objectId <- reader.resolveExistingUniqueId(AbbreviatedObjectId.fromString(m)).toOption
    } yield mapper.replacement(objectId).map(_.map(newId => m -> format(m, reader.abbreviate(newId, m.length).name)))).map(_.flatten.toMap)

    substitutionsFuture.map { substitutions =>
      if (substitutions.isEmpty) message else hexRegex.replaceSomeIn(message, m => substitutions.get(m.matched))
    }
  }
}