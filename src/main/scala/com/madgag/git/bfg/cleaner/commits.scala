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

import org.eclipse.jgit.lib._
import com.madgag.git.bfg.GitUtil._
import org.eclipse.jgit.revwalk.RevCommit
import scala.Some

object CommitCleaner {

  class Kit(objectDB: ObjectDatabase, val originalCommit: RevCommit, val mapper: CleaningMapper[ObjectId]) {
    lazy val objectReader = objectDB.newReader
  }

  def chain(cleaners: Seq[CommitCleaner]) = new CommitCleaner {
    override def fixer(kit: CommitCleaner.Kit) = Function.chain(cleaners.map(_.fixer(kit)))
  }
}

trait CommitCleaner {
  def fixer(kit: CommitCleaner.Kit): (CommitMessage => CommitMessage)
}



object FormerCommitFooter extends CommitCleaner {
  val Key = "Former-commit-id"

  override def fixer(kit: CommitCleaner.Kit) = _ add Footer(Key, kit.originalCommit.name)
}

//case class CommitStructure(parentIds: Seq[ObjectId], treeId: ObjectId)

object Footer {
  // ^[A-Za-z0-9-]+:
  val FooterPattern = """([\p{Alnum}-]+): *(.*)""".r

  def apply(footerLine: String): Option[Footer] = footerLine match {
    case FooterPattern(key, value) => Some(Footer(key, value))
    case _ => None
  }
}

case class Footer(key: String, value: String) {
  override lazy val toString = key + ": " + value
}

object CommitMessage {
  def apply(c: RevCommit): CommitMessage = CommitMessage(c.getAuthorIdent, c.getCommitterIdent, c.getFullMessage)
}

case class CommitMessage(author: PersonIdent, committer: PersonIdent, message: String) {
  lazy val lastParagraphBreak = message.lastIndexOf("\n\n")
  lazy val messageWithoutFooters = if (footers.isEmpty) message else (message take lastParagraphBreak)
  lazy val footers: List[Footer] = message.drop(lastParagraphBreak).lines.collect {
    case Footer.FooterPattern(key, value) => Footer(key, value)
  }.toList

  def add(footer: Footer) = copy(message = message + "\n" + (if (footers.isEmpty) "\n" else "") + footer.toString)

}