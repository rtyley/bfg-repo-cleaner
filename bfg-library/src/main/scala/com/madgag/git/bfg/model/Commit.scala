package com.madgag.git.bfg.model

import java.nio.charset.Charset
import com.madgag.git._
import com.madgag.git.bfg.cleaner._
import org.eclipse.jgit.lib.Constants.OBJ_COMMIT
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevWalk, RevCommit}

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


object Commit {
  def apply(revCommit: RevCommit): Commit = Commit(CommitNode(revCommit), revCommit.arcs)
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

  lazy val id = new ObjectInserter.Formatter().idFor(OBJ_COMMIT, toBytes)

  override lazy val toString = s"commit[${id.shortName}${node.subject.map(s=> s" '${s.take(50)}'").getOrElse("")}]"
}

case class CommitArcs(parents: Seq[ObjectId], tree: ObjectId) {
  def cleanWith(cleaner: ObjectIdCleaner) = CommitArcs(parents.map(cleaner.cleanCommit).filterNot(_ == ObjectId.zeroId()), cleaner.cleanTree(tree))

  def isEmptyCommit(implicit revWalk: RevWalk) = parents match {
    case Seq() => tree == Tree.Empty.objectId
    case Seq(singleParent) => singleParent.asRevCommit.getTree == tree
    case _ => false
  }

}

object CommitNode {
  def apply(c: RevCommit): CommitNode = CommitNode(c.getAuthorIdent, c.getCommitterIdent, c.getFullMessage, c.getEncoding)
}

case class CommitNode(author: PersonIdent, committer: PersonIdent, message: String, encoding: Charset = Constants.CHARSET) {
  lazy val subject = message.lines.toStream.headOption
  lazy val lastParagraphBreak = message.lastIndexOf("\n\n")
  lazy val messageWithoutFooters = if (footers.isEmpty) message else (message take lastParagraphBreak)
  lazy val footers: List[Footer] = message.drop(lastParagraphBreak).lines.collect {
    case Footer.FooterPattern(key, value) => Footer(key, value)
  }.toList

  def add(footer: Footer) = copy(message = message + "\n" + (if (footers.isEmpty) "\n" else "") + footer.toString)
}
