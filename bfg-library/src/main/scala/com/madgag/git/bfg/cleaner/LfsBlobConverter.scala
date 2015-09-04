/*
 * Copyright (c) 2015 Roberto Tyley
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

import java.nio.charset.Charset

import com.madgag.git.LFS._
import com.madgag.git._
import com.madgag.git.bfg.model._
import com.madgag.git.bfg.{MemoFunc, MemoUtil}
import com.madgag.textmatching.{Glob, TextMatcher}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.{ObjectId, ObjectReader}

import scala.util.Try
import scalax.file.ImplicitConversions._
import scalax.file.Path.createTempFile
import scalax.io.JavaConverters._

class LfsBlobConverter(
  lfsGlobExpression: String,
  repo: FileRepository
) extends TreeBlobModifier {

  val lfsObjectsDir = repo.getDirectory / LFS.ObjectsPath

  val threadLocalObjectDBResources = repo.getObjectDatabase.threadLocalResources

  val lfsGlob = TextMatcher(Glob, lfsGlobExpression)

  val lfsSuitableFiles: (FileName => Boolean) = f => lfsGlob(f.string)

  val gitAttributesLine = s"$lfsGlobExpression filter=lfs diff=lfs merge=lfs -text"

  implicit val UTF_8 = Charset.forName("UTF-8")

  val lfsPointerMemo = MemoUtil.concurrentCleanerMemo[ObjectId]()
  
  override def apply(dirtyBlobs: TreeBlobs) = {
    val cleanedBlobs = super.apply(dirtyBlobs)
    if (cleanedBlobs == dirtyBlobs) cleanedBlobs else ensureGitAttributesSetFor(cleanedBlobs)
  }

  def ensureGitAttributesSetFor(cleanedBlobs: TreeBlobs): TreeBlobs = {
    implicit lazy val inserter = threadLocalObjectDBResources.inserter()

    val newGitAttributesId = cleanedBlobs.entryMap.get(GitAttributesFileName).fold {
      storeBlob(gitAttributesLine)
    } {
      case (_, oldGitAttributesId) =>
        val objectLoader = threadLocalObjectDBResources.reader().open(oldGitAttributesId)
        val oldAttributes = objectLoader.getCachedBytes.asInput.lines().toSeq

        if (oldAttributes.contains(gitAttributesLine)) oldGitAttributesId else {
          storeBlob((oldAttributes :+ gitAttributesLine).mkString("\n"))
        }
    }
    cleanedBlobs.copy(entryMap = cleanedBlobs.entryMap + (GitAttributesFileName -> (RegularFile, newGitAttributesId)))
  }

  override def fix(entry: TreeBlobEntry) = {
    val cleanId = if (lfsSuitableFiles(entry.filename)) lfsPointerBlobIdForRealBlob(entry.objectId) else entry.objectId
    (entry.mode, cleanId)
  }

  val lfsPointerBlobIdForRealBlob: MemoFunc[ObjectId, ObjectId] = lfsPointerMemo { blobId: ObjectId =>
    implicit val reader = threadLocalObjectDBResources.reader()
    implicit lazy val inserter = threadLocalObjectDBResources.inserter()

    (for {
      blobSize <- blobId.sizeTry if blobSize > 512
      pointer <- tryStoringLfsFileFor(blobId)
    } yield storeBlob(pointer.bytes)).getOrElse(blobId)
  }

  def tryStoringLfsFileFor(blobId: ObjectId)(implicit r: ObjectReader): Try[Pointer] = {
    val loader = blobId.open
    
    val tmpFile = createTempFile(s"bfg.git-lfs.conv-${blobId.name}")
    
    val pointer = pointerFor(loader, tmpFile)

    val lfsPath = lfsObjectsDir / pointer.path

    val ensureLfsFile = Try(if (!lfsPath.exists) tmpFile moveTo lfsPath).recover {
      case _ if lfsPath.size.contains(loader.getSize) =>
    }

    Try(tmpFile.delete(force = true))

    ensureLfsFile.map(_ => pointer)
  }

}
