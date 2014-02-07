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

import org.eclipse.jgit.storage.file.WindowCacheConfig
import com.madgag.git.bfg.cleaner._
import scala.language.implicitConversions
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.RevWalk
import collection.convert.wrapAsScala._
import com.madgag.git._
import org.eclipse.jgit.internal.storage.file.ObjectDirectory
import org.eclipse.jgit.lib.ObjectReader._
import Constants.OBJ_BLOB
import com.madgag.git.SizedObject
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

trait CleaningMapper[V] extends Cleaner[V] {
  def isDirty(oldId: V): Future[Boolean] = for (newId <- apply(oldId)) yield newId != oldId

  def substitution(oldId: V): Future[Option[(V, V)]] = for (newId <- apply(oldId)) yield {
    if (newId == oldId) None else Some(oldId -> newId)
  }

  def replacement(oldId: V): Future[Option[V]] = for (newId <- apply(oldId)) yield {
    if (newId == oldId) None else Some(newId)
  }
}

trait BlockingCleaningMapper[V] extends BlockingCleaner[V] {
  def isDirty(oldId: V): Boolean = oldId != apply(oldId)

  def substitution(oldId: V): Option[(V, V)] = {
    val newId = apply(oldId)
    if (newId == oldId) None else Some(oldId -> newId)
  }

  def replacement(oldId: V): Option[V] = {
    val newId = apply(oldId)
    if (newId == oldId) None else Some(newId)
  }
}

object GitUtil {

  def tweakStaticJGitConfig(massiveNonFileObjects: Option[Int]) {
    val wcConfig: WindowCacheConfig = new WindowCacheConfig()
    wcConfig.setStreamFileThreshold(massiveNonFileObjects.getOrElse(1024 * 1024))
    wcConfig.install()
  }

  def hasBeenProcessedByBFGBefore(repo: Repository): Boolean = {
    // This method just checks the tips of all refs - a good-enough indicator for our purposes...
    implicit val revWalk = new RevWalk(repo)
    implicit val objectReader = revWalk.getObjectReader

    repo.getAllRefs.values.map(_.getObjectId).filter(_.open.getType == Constants.OBJ_COMMIT)
      .map(_.asRevCommit).exists(_.getFooterLines(FormerCommitFooter.Key).nonEmpty)
  }

  implicit def cleaner2CleaningMapper[V](f: Cleaner[V]): CleaningMapper[V] = new CleaningMapper[V] {
    def apply(v: V) = f(v)
  }

  implicit def cleaner2BlockingCleaningMapper[V](f: BlockingCleaner[V]): BlockingCleaningMapper[V] = new BlockingCleaningMapper[V] {
    def apply(v: V) = f(v)
  }

  def packedObjects(implicit objectDB: ObjectDirectory): Iterable[ObjectId] =
    for { pack <- objectDB.getPacks ; entry <- pack } yield entry.toObjectId

  def biggestBlobs(implicit objectDB: ObjectDirectory, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): Stream[SizedObject] = {
    Timing.measureTask("Scanning packfile for large blobs", ProgressMonitor.UNKNOWN) {
      val reader = objectDB.newReader
      packedObjects.map {
            objectId =>
              progressMonitor update 1
              SizedObject(objectId, reader.getObjectSize(objectId, OBJ_ANY))
          }.toSeq.sorted.reverse.toStream.filter(oid => reader.open(oid.objectId).getType == OBJ_BLOB)
    }
  }
}
