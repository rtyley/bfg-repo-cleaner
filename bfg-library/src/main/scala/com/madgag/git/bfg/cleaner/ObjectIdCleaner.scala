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

import org.eclipse.jgit.revwalk.{RevObject, RevWalk, RevTag, RevCommit}
import org.eclipse.jgit.lib.Constants._
import com.madgag.git.bfg.cleaner.protection.{ProtectedObjectDirtReport, ProtectedObjectCensus}
import com.madgag.git.bfg._
import com.madgag.git.bfg.model._
import com.madgag.git._
import bfg.model.Tree
import bfg.model.TreeSubtrees
import org.eclipse.jgit.lib._
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.model.Tree.Entry
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import com.madgag.git.bfg.model.TreeSubtrees

object ObjectIdCleaner {

  case class Config(protectedObjectCensus: ProtectedObjectCensus,
                    objectIdSubstitutor: ObjectIdSubstitutor = ObjectIdSubstitutor.OldIdsPublic,
                    commitNodeCleaners: Seq[CommitNodeCleaner] = Seq.empty,
                    treeEntryListCleaners: Seq[BlockingCleaner[Seq[Tree.Entry]]] = Seq.empty,
                    treeBlobsCleaners: Seq[BlockingCleaner[TreeBlobs]] = Seq.empty,
                    treeSubtreesCleaners: Seq[BlockingCleaner[TreeSubtrees]] = Seq.empty,
                    // messageCleaners? - covers both Tag and Commits
                    objectChecker: Option[ObjectChecker] = None) {

    lazy val commitNodeCleaner = CommitNodeCleaner.chain(commitNodeCleaners)

    lazy val treeEntryListCleaner = Function.chain(treeEntryListCleaners)

    lazy val treeBlobsCleaner = Function.chain(treeBlobsCleaners)

    lazy val treeSubtreesCleaner:BlockingCleaner[TreeSubtrees] = Function.chain(treeSubtreesCleaners)
  }

}

/*
 * Knows how to clean an object, and what objects are protected...
 */
class ObjectIdCleaner(config: ObjectIdCleaner.Config, objectDB: ObjectDatabase, implicit val revWalk: RevWalk) extends CleaningMapper[ObjectId] {

  import config._

  val threadLocalResources: ThreadLocalObjectDatabaseResources = objectDB.threadLocalResources

  // want to enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
  val memo: Memo[ObjectId, Future[ObjectId]] = MemoUtil.concurrentAsyncCleanerMemo

  val commitMemo: Memo[ObjectId, Future[ObjectId]] = MemoUtil.concurrentAsyncCleanerMemo
  val tagMemo: Memo[ObjectId, Future[ObjectId]] = MemoUtil.concurrentAsyncCleanerMemo

  val treeMemo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentBlockingCleanerMemo

  def apply(objectId: ObjectId): Future[ObjectId] = memoClean(objectId)

  /**
   * A cleaning function for types we know are synchronously-cleaned types (tree, blob)
   * A cleaning function for types we know are async-cleaned types (commit, tag)
   *
   * memo-ising must be applied to all functions that get called directly (ie, that might be called without
   * memoisation above them)
   */

  val memoClean = {
    val mc = memo { uncachedClean }
    protectedObjectCensus.fixedObjectIds.foreach(mc.fix)
    mc
  }

  def cleanedObjectMap(): Map[ObjectId, ObjectId] =
    cleanTree.asMap() ++ Seq(memoClean, cleanCommit, cleanTag).map(_.asMap().mapValues(_.value.get.get)).reduce(_ ++ _)

  def uncachedClean: Cleaner[ObjectId] = {
    objectId =>
      threadLocalResources.reader().open(objectId).getType match {
        case OBJ_TREE => Future.successful(cleanTree(objectId))
        case OBJ_COMMIT => cleanCommit(objectId)
        case OBJ_TAG => cleanTag(objectId)
        case _ => Future.successful(objectId) // we don't currently clean isolated blobs... only clean within a tree context
      }
  }

  def getCommit(commitId: AnyObjectId): RevCommit = revWalk synchronized (commitId asRevCommit)

  def getTag(tagId: AnyObjectId): RevTag = revWalk synchronized (tagId asRevTag)

  val cleanCommit: MemoFunc[ObjectId, Future[ObjectId]] = commitMemo { commitId =>
    val originalRevCommit = getCommit(commitId)
    val originalCommit = Commit(originalRevCommit)

    val cleanedArcsFuture = originalCommit.arcs cleanWith this
    for {
      updatedCommitNode <- commitNodeCleaner.fixer(new CommitNodeCleaner.Kit(threadLocalResources, originalRevCommit, originalCommit, cleanedArcsFuture, apply))(originalCommit.node)
      cleanedArcs <- cleanedArcsFuture
    } yield {
      val updatedCommit = Commit(updatedCommitNode, cleanedArcs)

      if (updatedCommit != originalCommit) insert(updatedCommit) else originalRevCommit
    }
  }

  private def insert(commit: Commit) = {
    val commitBytes = commit.toBytes
    objectChecker.foreach(_.checkCommit(commitBytes))
    threadLocalResources.inserter().insert(OBJ_COMMIT, commitBytes)
  }

  val cleanBlob: BlockingCleaner[ObjectId] = identity // Currently a NO-OP, we only clean at treeblob level

  val cleanTree: MemoFunc[ObjectId, ObjectId] = treeMemo { originalObjectId =>
    val entries = Tree.entriesFor(originalObjectId)(threadLocalResources.reader())
    val cleanedTreeEntries = treeEntryListCleaner(entries)

    val tree = Tree(cleanedTreeEntries)

    val fixedTreeBlobs = treeBlobsCleaner(tree.blobs)
    val cleanedSubtrees = TreeSubtrees(treeSubtreesCleaner(tree.subtrees).entryMap.map {
      case (name, treeId) => (name, cleanTree(treeId))
    }).withoutEmptyTrees

    if (entries != cleanedTreeEntries || fixedTreeBlobs != tree.blobs || cleanedSubtrees != tree.subtrees) {

      val updatedTree = tree copyWith(cleanedSubtrees, fixedTreeBlobs)

      val treeFormatter = updatedTree.formatter
      objectChecker.foreach(_.checkTree(treeFormatter.toByteArray))
      val updatedTreeId = treeFormatter.insertTo(threadLocalResources.inserter())

      updatedTreeId
    } else {
      originalObjectId
    }
  }

  val cleanTag: MemoFunc[ObjectId, Future[ObjectId]] = tagMemo { id =>
    val originalTag = getTag(id)

    val originalMessage = originalTag.getFullMessage
    val updatedMessageFuture = objectIdSubstitutor.replaceOldIds(originalMessage, threadLocalResources.reader(), apply)

    // TODO Clean message text even if tagged object has not changed
    val revObject = originalTag.getObject
    for {
      newObjectId <- apply(revObject)
      updatedMessage <- updatedMessageFuture
    } yield {
      if (newObjectId != revObject || updatedMessage != originalMessage) {
          val tb = new TagBuilder
          tb.setTag(originalTag.getTagName)
          tb.setObjectId(newObjectId, revObject.getType)
          tb.setTagger(originalTag.getTaggerIdent)
          tb.setMessage(updatedMessage)
          val cleanedTag: ObjectId = threadLocalResources.inserter().insert(tb)
          objectChecker.foreach(_.checkTag(tb.toByteArray))
          cleanedTag
      } else originalTag
    }
  }

  def stats() = Map("apply"->memoClean.stats(), "tree" -> cleanTree.stats(), "commit" -> cleanCommit.stats(), "tag" -> cleanTag.stats())

}
