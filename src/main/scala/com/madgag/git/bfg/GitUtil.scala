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

import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.lib.ObjectReader.OBJ_ANY
import org.eclipse.jgit.treewalk.TreeWalk
import collection.mutable
import org.eclipse.jgit.storage.file.{ObjectDirectory, FileRepository}
import scala.collection.JavaConversions._
import java.io.File
import scala.Some
import org.eclipse.jgit.util.FS

object GitUtil {
  implicit def fileRepository2ObjectDirectory(repo: FileRepository): ObjectDirectory = repo.getObjectDatabase

  implicit def objectIdSweetener(objectId: AnyObjectId): RichObjectId = new RichObjectId(objectId)

  implicit def objectReaderSweetener(reader: ObjectReader): RichObjectReader = new RichObjectReader(reader)

  class RichObjectId(objectId: AnyObjectId) {
    def asRevObject(implicit revWalk: RevWalk) = revWalk.parseAny(objectId)

    lazy val shortName = objectId.getName.take(8)
  }

  class RichObjectReader(reader: ObjectReader) {
    def resolveUniquely(id: AbbreviatedObjectId): Option[ObjectId] = Some(id).map(reader.resolve).filter(_.size == 1).map(_.toSeq.head)

    def resolveExistingUniqueId(id: AbbreviatedObjectId) = resolveUniquely(id).filter(reader.has)
  }

  def resolveGitDirFor(folder: File) = RepositoryCache.FileKey.resolve(folder, FS.detect)

  def allBlobsUnder(tree: RevTree)(implicit repo: Repository): Set[ObjectId] = {
    val treeWalk = new TreeWalk(repo)
    treeWalk.setRecursive(true)
    treeWalk.addTree(tree)
    val protectedIds = mutable.Set[ObjectId]()
    while (treeWalk.next) {
      protectedIds += treeWalk.getObjectId(0)
    }
    protectedIds.toSet
  }

  def allBlobsReachableFrom(revisions: Set[String])(implicit repo: Repository): Set[ObjectId] = {
    implicit val revWalk = new RevWalk(repo)

    (revisions.map {
      repo.resolve
    }.toSet.map {
      objectId: ObjectId => allBlobsReachableFrom(objectId.asRevObject)
    } fold Set.empty)(_ ++ _)
  }

  def allBlobsReachableFrom(revObject: RevObject)(implicit repo: Repository): Set[ObjectId] = {
    revObject match {
      case commit: RevCommit => allBlobsUnder(commit.getTree)
      case tree: RevTree => allBlobsUnder(tree)
      case blob: RevBlob => Set(blob.getId)
      case tag: RevTag => allBlobsReachableFrom(tag.getObject)
    }
  }

  def biggestBlobs(implicit objectDB: ObjectDirectory): Stream[(ObjectId, Long)] = {
    objectDB.getPacks.flatMap {
      _.map(_.toObjectId).map {
        objectId => objectId -> objectDB.newReader.getObjectSize(objectId, OBJ_ANY)
      }
    }.toStream.sortBy(_._2).reverse
  }
}
