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

package com.madgag.git.bfg

import com.google.common.primitives.Ints
import com.madgag.git.bfg.cleaner._
import com.madgag.git.{SizedObject, _}
import org.eclipse.jgit.internal.storage.file.ObjectDirectory
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import org.eclipse.jgit.lib.ObjectReader._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.WindowCacheConfig

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._
import scala.language.implicitConversions

trait CleaningMapper[V] extends Cleaner[V] {
  def isDirty(v: V) = apply(v) != v

  def substitution(oldId: V): Option[(V, V)] = {
    val newId = apply(oldId)
    if (newId == oldId) None else Some((oldId, newId))
  }

  def replacement(oldId: V): Option[V] = {
    val newId = apply(oldId)
    if (newId == oldId) None else Some(newId)
  }
}

object GitUtil {
  
  val ProbablyNoNonFileObjectsOverSizeThreshold: Long = 1024 * 1024
  
  def tweakStaticJGitConfig(massiveNonFileObjects: Option[Long]): Unit = {
    val wcConfig: WindowCacheConfig = new WindowCacheConfig()
    wcConfig.setStreamFileThreshold(Ints.saturatedCast(massiveNonFileObjects.getOrElse(ProbablyNoNonFileObjectsOverSizeThreshold)))
    wcConfig.install()
  }

  def hasBeenProcessedByBFGBefore(repo: Repository): Boolean = {
    // This method just checks the tips of all refs - a good-enough indicator for our purposes...
    implicit val revWalk = new RevWalk(repo)
    implicit val objectReader = revWalk.getObjectReader

    repo.getRefDatabase.getRefs().asScala.map(_.getObjectId).filter(_.open.getType == Constants.OBJ_COMMIT)
      .map(_.asRevCommit).exists(_.getFooterLines(FormerCommitFooter.Key).asScala.nonEmpty)
  }

  implicit def cleaner2CleaningMapper[V](f: Cleaner[V]): CleaningMapper[V] = new CleaningMapper[V] {
    def apply(v: V) = f(v)
  }

  def biggestBlobs(implicit objectDB: ObjectDirectory, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): LazyList[SizedObject] = {
    Timing.measureTask("Scanning packfile for large blobs", ProgressMonitor.UNKNOWN) {
      val reader = objectDB.newReader
      objectDB.packedObjects.map {
            objectId =>
              progressMonitor update 1
              SizedObject(objectId, reader.getObjectSize(objectId, OBJ_ANY))
          }.toSeq.sorted.reverse.to(LazyList).filter { oid =>
        oid.size > ProbablyNoNonFileObjectsOverSizeThreshold || reader.open(oid.objectId).getType == OBJ_BLOB
      }
    }
  }
}
