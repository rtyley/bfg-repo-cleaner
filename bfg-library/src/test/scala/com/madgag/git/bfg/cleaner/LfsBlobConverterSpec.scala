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

import com.madgag.diff.{After, Before, MapDiff}
import com.madgag.git.LFS.Pointer
import com.madgag.git._
import com.madgag.git.bfg.model.{TreeBlobs, BlobFileMode, FileName, Tree}
import com.madgag.git.test._
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.specs2.mutable._

import scalax.file.ImplicitConversions._

class LfsBlobConverterSpec extends Specification {

  "LfsBlobConverter" should {
    "successfully shift the blob to the LFS store" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      val oldTreeBlobs = Tree(repo.resolve("early-release^{tree}")).blobs

      val newTreeBlobs = clean(oldTreeBlobs, "*ero*")

      val diff = oldTreeBlobs.diff(newTreeBlobs)

      diff.changed mustEqual Set(FileName("one-kb-zeros"))
      diff.unchanged must contain(FileName("hero"), FileName("zero"))

      verifyPointersForChangedFiles(diff)
    }

    "not do damage if run twice - ie don't create a pointer for a pointer!" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      val oldTreeBlobs = Tree(repo.resolve("early-release^{tree}")).blobs

      val treeBlobsAfterRun1 = clean(oldTreeBlobs, "*ero*")

      val firstDiff = oldTreeBlobs.diff(treeBlobsAfterRun1)

      firstDiff.changed mustEqual Set(FileName("one-kb-zeros"))

      val treeBlobsAfterRun2 = clean(treeBlobsAfterRun1, "*ero*")

      treeBlobsAfterRun1.diff(treeBlobsAfterRun2).changed must beEmpty

      verifyPointersForChangedFiles(firstDiff) // Are the LFS files still intact?
    }
  }


  def clean(oldTreeBlobs: TreeBlobs, glob: String)(implicit repo: FileRepository): TreeBlobs = {
    val converter = new LfsBlobConverter(glob, repo)
    converter(oldTreeBlobs)
  }

  def verifyPointerInsertedFor(fileName: FileName, diff: MapDiff[FileName, (BlobFileMode, ObjectId)])(implicit repo: FileRepository) = {
    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    diff.changed must contain(fileName)

    val fileBeforeAndAfter = diff.changedMap(fileName)

    fileBeforeAndAfter(After)._1 mustEqual fileBeforeAndAfter(Before)._1

    val fileIds = fileBeforeAndAfter.mapValues(_._2)

    val (originalFileId, pointerObjectId) = (fileIds(Before), fileIds(After))

    verifyPointerFileFor(originalFileId, pointerObjectId)
  }

  def verifyPointerFileFor(originalFileId: ObjectId, pointerObjectId: ObjectId)(implicit repo: FileRepository) = {
    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    val pointer = Pointer.parse(pointerObjectId.open.getCachedBytes)

    val lfsStoredFile = repo.getDirectory / "lfs" / "objects" / pointer.path

    lfsStoredFile.exists must beTrue

    lfsStoredFile.size must beSome(pointer.blobSize)

    lfsStoredFile.bytes.toArray.blobId must eventually(be_==(originalFileId))
  }

  def verifyPointersForChangedFiles(diff: MapDiff[FileName, (BlobFileMode, ObjectId)])(implicit repo: FileRepository) = {
    forall(diff.changed) { fileName =>
      verifyPointerInsertedFor(fileName, diff)
    }
  }
}
