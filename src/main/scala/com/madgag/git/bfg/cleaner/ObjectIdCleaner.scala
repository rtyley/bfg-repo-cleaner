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

import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevWalk, RevTag, RevCommit}
import org.eclipse.jgit.lib.Constants._
import protection.ObjectProtection
import scalaz.Memo
import com.madgag.git.bfg.{CleaningMapper, MemoUtil}
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.model.{TreeSubtrees, Tree}

object ObjectIdCleaner {

  case class Config(objectProtection: ObjectProtection,
                    objectIdSubstitutor: ObjectIdSubstitutor,
                    commitNodeCleaners: Seq[CommitNodeCleaner] = Seq.empty,
                    treeBlobsCleaners: Seq[TreeBlobsCleaner] = Seq.empty,
                    objectChecker: Option[ObjectChecker] = None) {

    lazy val commitNodeCleaner = CommitNodeCleaner.chain(commitNodeCleaners)

    lazy val treeBlobsCleaner = TreeBlobsCleaner.chain(treeBlobsCleaners)
  }

}

class ObjectIdCleaner(config: ObjectIdCleaner.Config, objectDB: ObjectDatabase, implicit val revWalk: RevWalk) extends CleaningMapper[ObjectId] {

  import config._

  // want to enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
  val memo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentCleanerMemo(objectProtection.fixedObjectIds)

  def apply(objectId: ObjectId): ObjectId = memoClean(objectId)

  val memoClean = memo {
    uncachedClean
  }


  def uncachedClean: (ObjectId) => ObjectId = {
    objectId =>
      objectDB.newReader.open(objectId).getType match {
        case OBJ_COMMIT => cleanCommit(objectId)
        case OBJ_TREE => cleanTree(objectId)
        case OBJ_TAG => cleanTag(objectId)
        case _ => objectId // we don't currently clean isolated blobs... only clean within a tree context
      }
  }

  def getCommit(commitId: AnyObjectId): RevCommit = revWalk synchronized (commitId asRevCommit)

  def getTag(tagId: AnyObjectId): RevTag = revWalk synchronized (tagId asRevTag)

  def cleanCommit(commitId: ObjectId): ObjectId = {
    val originalRevCommit = getCommit(commitId)
    val originalCommit = Commit(originalRevCommit)

    val cleanedArcs = originalCommit.arcs cleanWith this
    val kit = new CommitNodeCleaner.Kit(objectDB, originalRevCommit, originalCommit, cleanedArcs, apply)
    val updatedCommitNode = commitNodeCleaner.fixer(kit)(originalCommit.node)
    val updatedCommit = Commit(updatedCommitNode, cleanedArcs)

    if (updatedCommit != originalCommit) {
      val commitBytes = updatedCommit.toBytes
      objectChecker.foreach(_.checkCommit(commitBytes))
      objectDB.newInserter.insert(OBJ_COMMIT, commitBytes)
    } else {
      originalRevCommit
    }
  }

  def cleanTree(originalObjectId: ObjectId): ObjectId = {
    val tree = Tree(originalObjectId, objectDB.newReader)

    val fixedTreeBlobs = treeBlobsCleaner.fixer(new TreeBlobsCleaner.Kit(objectDB))(tree.blobs)
    val cleanedSubtrees = TreeSubtrees(tree.subtrees.entryMap.map {
      case (name, treeId) => (name, apply(treeId))
    })

    if (fixedTreeBlobs != tree.blobs || cleanedSubtrees != tree.subtrees) {

      val updatedTree = tree copyWith(cleanedSubtrees, fixedTreeBlobs)

      val removedFiles = tree.blobs.entryMap -- fixedTreeBlobs.entryMap.keys
      //      val sizedRemovedFiles = removedFiles.mapValues {
      //        case (_, objectId) => SizedObject(objectId, objectDB.newReader.getObjectSize(objectId, ObjectReader.OBJ_ANY))
      //      }
      // allRemovedFiles ++= sizedRemovedFiles

      val treeFormatter = updatedTree.formatter
      objectChecker.foreach(_.checkTree(treeFormatter.toByteArray))
      val updatedTreeId = treeFormatter.insertTo(objectDB.newInserter)

      updatedTreeId
    } else {
      originalObjectId
    }
  }

  def cleanTag(id: ObjectId): ObjectId = {
    val originalTag = getTag(id)

    replacement(originalTag.getObject).map {
      cleanedObj =>
        val tb = new TagBuilder
        tb.setTag(originalTag.getTagName)
        tb.setObjectId(cleanedObj, originalTag.getObject.getType)
        tb.setTagger(originalTag.getTaggerIdent)
        tb.setMessage(objectIdSubstitutor.replaceOldIds(originalTag.getFullMessage, objectDB.newReader, apply))
        val cleanTag: ObjectId = objectDB.newInserter.insert(tb)
        objectChecker.foreach(_.checkTag(tb.toByteArray))
        cleanTag
    }.getOrElse(originalTag)
  }
}
