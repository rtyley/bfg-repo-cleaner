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

import com.madgag.git._
import com.madgag.git.bfg.Timing
import org.eclipse.jgit.lib.{ObjectId, ProgressMonitor, RefDatabase}
import org.eclipse.jgit.revwalk.RevSort._
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.transport.ReceiveCommand

import scala.jdk.CollectionConverters._
import scala.collection.parallel.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

object RepoRewriter {

  def rewrite(repo: org.eclipse.jgit.lib.Repository, objectIdCleanerConfig: ObjectIdCleaner.Config): Map[ObjectId, ObjectId] = {
    implicit val refDatabase: RefDatabase = repo.getRefDatabase

    assert(refDatabase.hasRefs, "Can't find any refs in repo at " + repo.getDirectory.getAbsolutePath)

    val reporter: Reporter = new CLIReporter(repo)
    implicit val progressMonitor: ProgressMonitor = reporter.progressMonitor

    val allRefs = refDatabase.getRefs().asScala

    def createRevWalk: RevWalk = {

      val revWalk = new RevWalk(repo)

      revWalk.sort(TOPO) // crucial to ensure we visit parents BEFORE children, otherwise blow stack
      revWalk.sort(REVERSE, true) // we want to start with the earliest commits and work our way up...

      val startCommits = allRefs.map(_.targetObjectId.asRevObject(revWalk)).collect { case c: RevCommit => c }

      revWalk.markStart(startCommits.asJavaCollection)
      revWalk
    }

    implicit val revWalk = createRevWalk
    implicit val reader = revWalk.getObjectReader

    reporter.reportRefsForScan(allRefs)

    reporter.reportObjectProtection(objectIdCleanerConfig)(repo.getObjectDatabase, revWalk)

    val objectIdCleaner = new ObjectIdCleaner(objectIdCleanerConfig, repo.getObjectDatabase, revWalk)

    val commits = revWalk.asScala.toSeq

    def clean(commits: Seq[RevCommit]): Unit = {
      reporter.reportCleaningStart(commits)

      Timing.measureTask("Cleaning commits", commits.size) {
        Future {
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
    }

    def updateRefsWithCleanedIds(): Unit = {
      val refUpdateCommands = for (ref <- repo.nonSymbolicRefs;
                                   (oldId, newId) <- objectIdCleaner.substitution(ref.getObjectId)
      ) yield new ReceiveCommand(oldId, newId, ref.getName)

      if (refUpdateCommands.isEmpty) {
        println("\nBFG aborting: No refs to update - no dirty commits found??\n")
      } else {
        reporter.reportRefUpdateStart(refUpdateCommands)

        Timing.measureTask("...Ref update", refUpdateCommands.size) {
          // Hack a fix for issue #23 : Short-cut the calculation that determines an update is NON-FF
          val quickMergeCalcRevWalk = new RevWalk(revWalk.getObjectReader) {
            override def isMergedInto(base: RevCommit, tip: RevCommit) =
              if (tip == objectIdCleaner(base)) false else super.isMergedInto(base, tip)
          }

          refDatabase.newBatchUpdate.setAllowNonFastForwards(true).addCommand(refUpdateCommands.asJavaCollection)
            .execute(quickMergeCalcRevWalk, progressMonitor)
        }

        reporter.reportResults(commits, objectIdCleaner)
      }
    }


    clean(commits)

    updateRefsWithCleanedIds()

    objectIdCleaner.stats()

    objectIdCleaner.cleanedObjectMap()
  }

}
