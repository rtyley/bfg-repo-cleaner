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

import com.google.common.io.ByteSource
import com.google.common.io.Files.createParentDirs
import com.madgag.git.LFS._
import com.madgag.git._
import com.madgag.git.bfg.model._
import com.madgag.git.bfg.{MemoFunc, MemoUtil}
import com.madgag.textmatching.{Glob, TextMatcher}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.{ObjectId, ObjectReader}

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path}
import scala.jdk.StreamConverters._
import scala.util.{Try, Using}

class LfsBlobConverter(
  lfsGlobExpression: String,
  repo: FileRepository
) extends TreeBlobModifier {

  val lfsObjectsDir: Path = repo.getDirectory.toPath.resolve(LFS.ObjectsPath)

  val threadLocalObjectDBResources = repo.getObjectDatabase.threadLocalResources

  val lfsGlob = TextMatcher(Glob, lfsGlobExpression)

  val lfsSuitableFiles: (FileName => Boolean) = f => lfsGlob(f.string)

  val gitAttributesLine = s"$lfsGlobExpression filter=lfs diff=lfs merge=lfs -text"

  implicit val UTF_8: Charset = StandardCharsets.UTF_8

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
        Using(ByteSource.wrap(objectLoader.getCachedBytes).asCharSource(UTF_8).lines()) { oldAttributesStream =>
          val oldAttributes = oldAttributesStream.toScala(Seq)
          if (oldAttributes.contains(gitAttributesLine)) oldGitAttributesId else {
            storeBlob((oldAttributes :+ gitAttributesLine).mkString("\n"))
          }
        }.get
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
    
    val tmpFile: Path = Files.createTempFile(s"bfg.git-lfs.conv-${blobId.name}","dat")
    
    val pointer = pointerFor(loader, tmpFile)

    val lfsPath = lfsObjectsDir.resolve(pointer.path)

    createParentDirs(lfsPath.toFile)

    val ensureLfsFile = Try(if (!Files.exists(lfsPath)) Files.move(tmpFile, lfsPath)).recover {
      case _ if Files.exists(lfsPath) && Files.size(lfsPath) == loader.getSize =>
    }

    Try(Files.deleteIfExists(tmpFile))

    ensureLfsFile.map(_ => pointer)
  }

}
