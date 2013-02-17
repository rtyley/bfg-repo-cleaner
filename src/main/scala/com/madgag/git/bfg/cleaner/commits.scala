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
import org.eclipse.jgit.revwalk.RevCommit
import scala.Some
import java.nio.charset.Charset

object CommitNodeCleaner {

  class Kit(objectDB: ObjectDatabase,
            val originalRevCommit: RevCommit,
            val originalCommit: Commit,
            val updatedArcs: CommitArcs,
            val mapper: Cleaner[ObjectId]) {
    lazy val objectReader = objectDB.newReader

    val arcsChanged = originalCommit.arcs != updatedArcs

    def commitIsChanged(withThisNode: CommitNode) = arcsChanged || originalCommit.node != withThisNode
  }

  def chain(cleaners: Seq[CommitNodeCleaner]) = new CommitNodeCleaner {
    override def fixer(kit: CommitNodeCleaner.Kit) = Function.chain(cleaners.map(_.fixer(kit)))
  }
}

trait CommitNodeCleaner {
  def fixer(kit: CommitNodeCleaner.Kit): Cleaner[CommitNode]
}

object FormerCommitFooter extends CommitNodeCleaner {
  val Key = "Former-commit-id"

  override def fixer(kit: CommitNodeCleaner.Kit) = modifyIf(kit.commitIsChanged) {
    _ add Footer(Key, kit.originalRevCommit.name)
  }

  def modifyIf[A](predicate: A => Boolean)(modifier: A => A): (A => A) = v => if (predicate(v)) modifier(v) else v
}

object Commit {
  def apply(revCommit: RevCommit): Commit = Commit(CommitNode(revCommit), CommitArcs(revCommit))
}

case class Commit(node: CommitNode, arcs: CommitArcs) {
  def toBytes: Array[Byte] = {
    import scala.collection.convert.wrapAsJava._

    val c = new CommitBuilder
    c.setParentIds(arcs.parents)
    c.setTreeId(arcs.tree)

    c.setAuthor(node.author)
    c.setCommitter(node.committer)
    c.setEncoding(node.encoding)
    c.setMessage(node.message)

    c.toByteArray
  }
}

object CommitArcs {
  def apply(revCommit: RevCommit): CommitArcs = CommitArcs(revCommit.getParents, revCommit.getTree)
}

case class CommitArcs(parents: Seq[ObjectId], tree: ObjectId) {
  def cleanWith(cleaner: Cleaner[ObjectId]) = CommitArcs(parents map cleaner, cleaner(tree))
}

object CommitNode {
  def apply(c: RevCommit): CommitNode = CommitNode(c.getAuthorIdent, c.getCommitterIdent, c.getFullMessage, c.getEncoding)
}

case class CommitNode(author: PersonIdent, committer: PersonIdent, message: String, encoding: Charset = Constants.CHARSET) {
  lazy val lastParagraphBreak = message.lastIndexOf("\n\n")
  lazy val messageWithoutFooters = if (footers.isEmpty) message else (message take lastParagraphBreak)
  lazy val footers: List[Footer] = message.drop(lastParagraphBreak).lines.collect {
    case Footer.FooterPattern(key, value) => Footer(key, value)
  }.toList

  def add(footer: Footer) = copy(message = message + "\n" + (if (footers.isEmpty) "\n" else "") + footer.toString)
}

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
