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

import org.eclipse.jgit.revwalk.{RevTag, RevWalk, RevCommit}
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import scalaz.Memo
import org.eclipse.jgit.lib.Constants.{OBJ_TREE, OBJ_COMMIT, OBJ_TAG}
import java.io.InputStream
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.revwalk.RevSort.TOPO
import com.madgag.git.bfg.model._
import com.madgag.git.bfg.MemoUtil
import com.madgag.git.bfg.GitUtil._
import org.eclipse.jgit.lib._
import com.madgag.git.bfg.model.TreeSubtrees
import com.madgag.git.bfg.model.Tree
import actors.Actor

/*
Encountering a blob ->
BIG-BLOB-DELETION : Either 'good' or 'delete'   - or possibly replace, with a different filename (means tree-level)
PASSWORD-REMOVAL : Either 'good' or 'replace'

Encountering a tree ->
BIG-BLOB-DELETION : Either 'good' or 'replace'   - possibly adding with a different placeholder blob entry
PASSWORD-REMOVAL : Either 'good' or 'replace' - replacing one blob entry with another

So if we encounter a tree, we are unlikely to want to remove that tree entirely...
SHOULD WE JUST DISALLOW THAT?
Obviously, a Commit HAS to have a tree, so it's dangerous to allow a None response to tree transformation

An objectId must be either GOOD or BAD, and we must never translate *either* kind of id into a BAD

User-customisation interface: TreeBlobs => TreeBlobs

User gets no say in adding, renaming, removing directories

TWO MAIN USE CASES FOR HISTORY-CHANGING ARE:
1: GETTING RID OF BIG BLOBS
2: REMOVING PASSWORDS IN HISTORICAL FILES

possible other use-case: fixing committer names - and possibly removing passwords from commits? (could possibly just be done with rebase)

Why else would you want to rewrite HISTORY? Many other changes (ie putting a directory one down) need only be applied
in a new commit, we don't care about history.

When updaing a Tree, the User has no right to muck with sub-trees. They can only alter the blob contents.
 */

trait BlobInserter {
  def insert(data: Array[Byte]): ObjectId

  def insert(length: Long, in: InputStream): ObjectId
}

object RepoRewriter {

  def rewrite(repo: org.eclipse.jgit.lib.Repository, treeCleaner: TreeCleaner) {

    assert(!repo.getAllRefs.isEmpty, "Can't find any refs in repo at " + repo.getDirectory.getAbsolutePath)
    val progressMonitor = new TextProgressMonitor
    val objectChecker = new ObjectChecker()
    val objectDB = repo.getObjectDatabase

    // want to enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
    val memo: Memo[ObjectId, ObjectId] = MemoUtil.concurrentHashMapMemo


    implicit val revWalk = new RevWalk(repo)
    revWalk.sort(TOPO) // crucial to ensure we visit parents BEFORE children, otherwise blow stack

    val commits = {
      import scala.collection.JavaConversions._

      val objReader = objectDB.newReader

      val refsByObjType = repo.getAllRefs.values.groupBy {
        ref => objReader.open(ref.getObjectId).getType
      } withDefault Seq.empty

      refsByObjType.foreach {
        case (typ, refs) => println("Found " + refs.size + " " + Constants.typeString(typ) + "-pointing refs")
      }

      revWalk.markStart(refsByObjType(OBJ_COMMIT).map(ref => ref.getObjectId.asRevCommit))
      // revWalk.markStart(refsByObjType(OBJ_TAG).map(_.getPeeledObjectId).filter(id=>objectDB.open(id).getType==OBJ_COMMIT).map(revWalk.lookupCommit(_)))

      println("Getting full commit list:")
      revWalk.toList.reverse // we want to start with the earliest commits and work our way up...
    }

    println("Found " + commits.size + " commits")


    def getCommit(commitId: AnyObjectId): RevCommit = {
      revWalk.synchronized {
        revWalk.parseCommit(commitId)
      }
    }

    def getTag(tagId: AnyObjectId): RevTag = {
      revWalk.synchronized {
        revWalk.parseTag(tagId)
      }
    }

    def newInserter = objectDB.newInserter

    def cleanTag(id: ObjectId): ObjectId = {
      val originalTag = getTag(id)

      val originalObj = originalTag.getObject

      val cleanedObj = memoCleanObjectFor(originalObj)

      if (cleanedObj != originalObj) {
        val tb = new TagBuilder
        tb.setTag(originalTag.getTagName)
        tb.setObjectId(cleanedObj, originalTag.getObject.getType)
        tb.setTagger(originalTag.getTaggerIdent)
        tb.setMessage(originalTag.getFullMessage)
        val cleanTag: ObjectId = newInserter.insert(tb)
        cleanTag
      } else {
        originalTag
      }
    }

    lazy val commitMessageCleaner = CommitCleaner.chain(Seq(ObjectIdSubstititor, FormerCommitFooter))

    lazy val mapper = new CleaningMapper[ObjectId] {
      lazy val clean = memoCleanObjectFor
    }

    def cleanCommit(commitId: ObjectId): ObjectId = {
      import scala.collection.JavaConversions._

      val originalCommit = getCommit(commitId)

      val originalTree = originalCommit.getTree
      val cleanedTree = memoCleanObjectFor(originalCommit.getTree)

      val originalParentCommits = originalCommit.getParents.toList
      val cleanedParentCommits = originalParentCommits.map(memoCleanObjectFor).seq

      if (cleanedParentCommits != originalParentCommits || cleanedTree != originalTree) {
        val c = new CommitBuilder
        c.setEncoding(originalCommit.getEncoding)
        c.setParentIds(cleanedParentCommits)
        c.setTreeId(cleanedTree)
        val kit = new CommitCleaner.Kit(objectDB, originalCommit, mapper)
        val updatedCommit = commitMessageCleaner.fixer(kit)(CommitMessage(originalCommit))

        c.setAuthor(updatedCommit.author)
        c.setCommitter(updatedCommit.committer)
        c.setMessage(updatedCommit.message)
        val cleanCommit = newInserter.insert(c)
        // objectChecker.checkCommit(c.toByteArray)
        cleanCommit
      } else {
        originalCommit
      }
    }

    lazy val memoCleanObjectFor: (ObjectId) => ObjectId =
      memo {
        objectId =>
        // print(".")
        // pass reader through to cleaners?
          objectDB.newReader.open(objectId).getType match {
            case OBJ_COMMIT => cleanCommit(objectId)
            case OBJ_TREE => cleanTree(objectId)
            case OBJ_TAG => cleanTag(objectId)
            case _ => objectId
          }
      }

    lazy val allRemovedFiles = collection.mutable.Map[FileName, SizedObject]()

    def cleanTree(originalObjectId: ObjectId): ObjectId = {
      val parser = new CanonicalTreeParser
      val reader = objectDB.newReader
      parser.reset(reader, originalObjectId)

      val tree = Tree(parser)

      val cleanedSubtrees = TreeSubtrees(tree.subtrees.entryMap.map {
        case (name, treeId) => (name, memoCleanObjectFor(treeId))
      }.seq)

      val hunterFixedTreeBlobs: TreeBlobs = treeCleaner fix(tree.blobs, new TreeCleaner.Kit(objectDB))

      if (hunterFixedTreeBlobs != tree.blobs || cleanedSubtrees != tree.subtrees) {

        val updatedTree = tree copyWith(cleanedSubtrees, hunterFixedTreeBlobs)

        val removedFiles = tree.blobs.entryMap -- hunterFixedTreeBlobs.entryMap.keys
        val sizedRemovedFiles = removedFiles.mapValues {
          case (_, objectId) => SizedObject(objectId, reader.getObjectSize(objectId, ObjectReader.OBJ_ANY))
        }
        allRemovedFiles ++= sizedRemovedFiles
        // objectChecker.checkTree(updatedTree.formatter.toByteArray) // throws exception if bad

        val updatedTreeId = updatedTree.formatter.insertTo(newInserter)

        updatedTreeId
      } else {
        originalObjectId
      }
    }

    new Actor {
      override def act() = commits.par.foreach {
        commit => memoCleanObjectFor(commit.getTree)
      }
    }.start

    progressMonitor.beginTask("Cleaning commits", commits.size)
    commits.foreach {
      commit =>
        memoCleanObjectFor(commit)
        progressMonitor update 1
    }
    progressMonitor.endTask()

    println("\nRefs\n")

    {
      import scala.collection.JavaConversions._
      val refUpdateCommands = repo.getAllRefs.values.filterNot(_.isSymbolic).filter(ref => mapper.isDirty(ref.getObjectId)).map {
        ref =>
          new ReceiveCommand(ref.getObjectId, memoCleanObjectFor(ref.getObjectId), ref.getName)
      }


      repo.getRefDatabase.newBatchUpdate.setAllowNonFastForwards(true).addCommand(refUpdateCommands).execute(revWalk, progressMonitor)
    }

    println("\nPost-update allRemovedFiles.size=" + allRemovedFiles.size)

    // allRemovedFiles.toSeq.sortBy(_._2).foreach { case (name,SizedObject(id,size)) => println(id.shortName+"\t"+size+"\t"+name) }
  }

}