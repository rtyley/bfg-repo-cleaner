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

import org.eclipse.jgit.revwalk.{RevSort, RevWalk, RevCommit}
import org.eclipse.jgit.lib.Constants.OBJ_COMMIT
import java.io.InputStream
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.revwalk.RevSort._
import com.madgag.git.bfg.model._
import com.madgag.git.bfg.Timing
import com.madgag.git.bfg.GitUtil._
import org.eclipse.jgit.lib._
import concurrent.future
import concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

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

When updating a Tree, the User has no right to muck with sub-trees. They can only alter the blob contents.
 */

trait BlobInserter {
  def insert(data: Array[Byte]): ObjectId

  def insert(length: Long, in: InputStream): ObjectId
}

object RepoRewriter {

  def rewrite(repo: org.eclipse.jgit.lib.Repository, objectIdCleanerConfig: ObjectIdCleaner.Config) {
    assert(!repo.getAllRefs.isEmpty, "Can't find any refs in repo at " + repo.getDirectory.getAbsolutePath)
    implicit val progressMonitor = new TextProgressMonitor
    val objectDB = repo.getObjectDatabase

    def createRevWalk: RevWalk = {

      val revWalk = new RevWalk(repo)
      revWalk.sort(TOPO) // crucial to ensure we visit parents BEFORE children, otherwise blow stack
      revWalk.sort(REVERSE, true) // we want to start with the earliest commits and work our way up...
      val objReader = objectDB.newReader

      val refsByObjType = repo.getAllRefs.values.groupBy {
        ref => objReader.open(ref.getObjectId).getType
      } withDefault Seq.empty

      refsByObjType.foreach {
        case (typ, refs) => println("Found " + refs.size + " " + Constants.typeString(typ) + "-pointing refs")
      }

      revWalk.markStart(refsByObjType(OBJ_COMMIT).map(ref => ref.getObjectId.asRevCommit(revWalk)))
      // revWalk.markStart(refsByObjType(OBJ_TAG).map(_.getPeeledObjectId).filter(id=>objectDB.open(id).getType==OBJ_COMMIT).map(revWalk.lookupCommit(_)))
      revWalk
    }

    implicit val revWalk = createRevWalk

    println("Getting full commit list:")
    val commits = revWalk.toList
    println("Found " + commits.size + " commits")

    val objectIdCleaner = new ObjectIdCleaner(objectIdCleanerConfig, repo.getObjectDatabase , revWalk)

    // lazy val allRemovedFiles = collection.mutable.Map[FileName, SizedObject]()

    Timing.measureTask("Cleaning commits", commits.size) {
      future {
        commits.par.foreach {
          commit => objectIdCleaner(commit.getTree)
        }
      }

      commits.foreach {
        commit =>
          objectIdCleaner(commit)
          progressMonitor update 1
      }
    }

    reportTreeDirtHistory(commits, objectIdCleaner)

    println("\nRefs\n")

    {
      import scala.collection.JavaConversions._

      val refUpdateCommands = for (ref <- repo.getAllRefs.values if !ref.isSymbolic;
                                   (oldId, newId) <- objectIdCleaner.substitution(ref.getObjectId)
      ) yield (new ReceiveCommand(oldId, newId, ref.getName))

      repo.getRefDatabase.newBatchUpdate.setAllowNonFastForwards(true).addCommand(refUpdateCommands).execute(revWalk, progressMonitor)
    }

    // ("\nPost-update allRemovedFiles.size=" + allRemovedFiles.size)

    // allRemovedFiles.toSeq.sortBy(_._2).foreach { case (name,SizedObject(id,size)) => println(id.shortName+"\t"+size+"\t"+name) }
  }

  def reportTreeDirtHistory(commits: List[RevCommit], objectIdCleaner: ObjectId => ObjectId) {
    def title(text: String) = s"\n$text\n" + ("-" * text.size) + "\n"
    val dirtHistoryElements = 60
    def cut[A](xs: Seq[A], n: Int) = {
      val avgSize = xs.size.toFloat / n
      def startOf(unit: Int): Int = math.round(unit * avgSize)
      (0 until n).view.map(u => xs.slice(startOf(u), startOf(u + 1)))
    }
    val treeDirtHistory = cut(commits, dirtHistoryElements).map(_.exists(c => objectIdCleaner(c.getTree) != c.getTree)).map(if (_) 'D' else '.').mkString
    def leftRight(markers: Seq[String]) = markers.mkString(" " * (dirtHistoryElements - markers.map(_.size).sum))
    println(title("Commit Tree-Dirt History"))
    println("\t" + leftRight(Seq("Earliest", "Latest")))
    println("\t" + leftRight(Seq("|", "|")))
    println("\t" + treeDirtHistory)
    println("\n\tD = dirty commits (file tree fixed)")
    println("\t. = clean commits (no changes to file tree)\n")
  }

}