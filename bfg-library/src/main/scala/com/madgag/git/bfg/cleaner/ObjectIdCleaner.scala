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

import com.madgag.collection.concurrent.ConcurrentMultiMap
import com.madgag.git._
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner.protection.{ProtectedObjectCensus, ProtectedObjectDirtReport}
import com.madgag.git.bfg.model.{Tree, TreeSubtrees, _}
import com.madgag.git.bfg.{CleaningMapper, Memo, MemoFunc, MemoUtil}
import org.eclipse.jgit.lib.Constants._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevCommit, RevTag, RevWalk}

object ObjectIdCleaner {

  case class Config(protectedObjectCensus: ProtectedObjectCensus,
                    objectIdSubstitutor: ObjectIdSubstitutor = ObjectIdSubstitutor.OldIdsPublic,
                    commitNodeCleaners: Seq[CommitNodeCleaner] = Seq.empty,
                    treeEntryListCleaners: Seq[Cleaner[Seq[Tree.Entry]]] = Seq.empty,
                    treeBlobsCleaners: Seq[Cleaner[TreeBlobs]] = Seq.empty,
                    treeSubtreesCleaners: Seq[Cleaner[TreeSubtrees]] = Seq.empty,
                    // messageCleaners? - covers both Tag and Commits
                    objectChecker: Option[ObjectChecker] = None) {

    lazy val commitNodeCleaner = CommitNodeCleaner.chain(commitNodeCleaners)

    lazy val treeEntryListCleaner = Function.chain(treeEntryListCleaners)

    lazy val treeBlobsCleaner = Function.chain(treeBlobsCleaners)

    lazy val treeSubtreesCleaner:Cleaner[TreeSubtrees] = Function.chain(treeSubtreesCleaners)
  }

}

/*
 * Knows how to clean an object, and what objects are protected...
 */
class ObjectIdCleaner(config: ObjectIdCleaner.Config, objectDB: ObjectDatabase, implicit val revWalk: RevWalk) extends CleaningMapper[ObjectId] {

  import config._

  val threadLocalResources = objectDB.threadLocalResources

  val changesByFilename = new ConcurrentMultiMap[FileName, (ObjectId, ObjectId)]
  val deletionsByFilename = new ConcurrentMultiMap[FileName, ObjectId]

  // want to enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
  val memo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentCleanerMemo(protectedObjectCensus.fixedObjectIds)

  val commitMemo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentCleanerMemo()
  val tagMemo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentCleanerMemo()

  val treeMemo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentCleanerMemo(protectedObjectCensus.treeIds.toSet[ObjectId])

  def apply(objectId: ObjectId): ObjectId = memoClean(objectId)

  val memoClean = memo {
    uncachedClean
  }

  def cleanedObjectMap(): Map[ObjectId, ObjectId] =
    Seq(memoClean, cleanCommit, cleanTag, cleanTree).map(_.asMap()).reduce(_ ++ _)

  def uncachedClean: (ObjectId) => ObjectId = {
    objectId =>
      threadLocalResources.reader().open(objectId).getType match {
        case OBJ_COMMIT => cleanCommit(objectId)
        case OBJ_TREE => cleanTree(objectId)
        case OBJ_TAG => cleanTag(objectId)
        case _ => objectId // we don't currently clean isolated blobs... only clean within a tree context
      }
  }

  def getCommit(commitId: AnyObjectId): RevCommit = revWalk synchronized (commitId asRevCommit)

  def getTag(tagId: AnyObjectId): RevTag = revWalk synchronized (tagId asRevTag)

  val cleanCommit: MemoFunc[ObjectId, ObjectId] = commitMemo { commitId =>
    val originalRevCommit = getCommit(commitId)
    val originalCommit = Commit(originalRevCommit)

    val cleanedArcs = originalCommit.arcs cleanWith this
    val kit = new CommitNodeCleaner.Kit(threadLocalResources, originalRevCommit, originalCommit, cleanedArcs, apply)
    val updatedCommitNode = commitNodeCleaner.fixer(kit)(originalCommit.node)
    val updatedCommit = Commit(updatedCommitNode, cleanedArcs)

    if (updatedCommit != originalCommit) {
      val commitBytes = updatedCommit.toBytes
      objectChecker.foreach(_.checkCommit(commitBytes))
      threadLocalResources.inserter().insert(OBJ_COMMIT, commitBytes)
    } else {
      originalRevCommit
    }
  }

  val cleanBlob: Cleaner[ObjectId] = identity // Currently a NO-OP, we only clean at treeblob level

  val cleanTree: MemoFunc[ObjectId, ObjectId] = treeMemo { originalObjectId =>
    val entries = Tree.entriesFor(originalObjectId)(threadLocalResources.reader())
    val cleanedTreeEntries = treeEntryListCleaner(entries)

    val tree = Tree(cleanedTreeEntries)

    val originalBlobs = tree.blobs
    val fixedTreeBlobs = treeBlobsCleaner(originalBlobs)
    val cleanedSubtrees = TreeSubtrees(treeSubtreesCleaner(tree.subtrees).entryMap.map {
      case (name, treeId) => (name, cleanTree(treeId))
    }).withoutEmptyTrees

    val treeBlobsChanged = fixedTreeBlobs != originalBlobs
    if (entries == cleanedTreeEntries && !treeBlobsChanged && cleanedSubtrees == tree.subtrees) originalObjectId else {
      if (treeBlobsChanged) recordChange(originalBlobs, fixedTreeBlobs)

      val updatedTree = tree copyWith(cleanedSubtrees, fixedTreeBlobs)

      val treeFormatter = updatedTree.formatter
      objectChecker.foreach(_.checkTree(treeFormatter.toByteArray))
      treeFormatter.insertTo(threadLocalResources.inserter())
    }
  }

  def recordChange(originalBlobs: TreeBlobs, fixedTreeBlobs: TreeBlobs): Unit = {
    val changedFiles: Set[TreeBlobEntry] = originalBlobs.entries.toSet -- fixedTreeBlobs.entries.toSet
    for (TreeBlobEntry(filename, _, oldId) <- changedFiles) {
      fixedTreeBlobs.objectId(filename) match {
        case Some(newId) => changesByFilename.addBinding(filename, (oldId, newId))
        case None => deletionsByFilename.addBinding(filename, oldId)
      }
    }
  }

  case class TreeBlobChange(oldId: ObjectId, newIdOpt: Option[ObjectId], filename: FileName)

  val cleanTag: MemoFunc[ObjectId, ObjectId] = tagMemo { id =>
    val originalTag = getTag(id)

    replacement(originalTag.getObject).map {
      cleanedObj =>
        val tb = new TagBuilder
        tb.setTag(originalTag.getTagName)
        tb.setObjectId(cleanedObj, originalTag.getObject.getType)
        tb.setTagger(originalTag.getTaggerIdent)
        tb.setMessage(objectIdSubstitutor.replaceOldIds(originalTag.getFullMessage, threadLocalResources.reader(), apply))
        val cleanedTag: ObjectId = threadLocalResources.inserter().insert(tb)
        objectChecker.foreach(_.checkTag(tb.build()))
        cleanedTag
    }.getOrElse(originalTag)
  }

  lazy val protectedDirt: Seq[ProtectedObjectDirtReport] = {
    protectedObjectCensus.protectorRevsByObject.map {
      case (protectedRevObj, refNames) =>
        val originalContentObject = treeOrBlobPointedToBy(protectedRevObj).merge
        val replacementTreeOrBlob = uncachedClean.replacement(originalContentObject)
        ProtectedObjectDirtReport(protectedRevObj, originalContentObject, replacementTreeOrBlob)
    }.toList
  }

  def stats() = Map("apply"->memoClean.stats(), "tree" -> cleanTree.stats(), "commit" -> cleanCommit.stats(), "tag" -> cleanTag.stats())

}
