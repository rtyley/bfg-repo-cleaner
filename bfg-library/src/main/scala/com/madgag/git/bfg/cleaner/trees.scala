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

import org.eclipse.jgit.lib.{ObjectReader, ObjectStream, ObjectId}
import com.madgag.git.bfg.model._
import org.eclipse.jgit.diff.RawText
import java.io._
import scalaz.Memo
import com.madgag.git.bfg.MemoUtil
import scalax.io.Resource
import java.nio.charset.Charset
import BlobTextModifier._
import scalax.io.managed.InputStreamResource
import java.nio.ByteBuffer
import util.Try
import java.nio.charset.CodingErrorAction._
import com.madgag.git.bfg.cleaner.kit.BlobInserter
import scala.Some
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.ThreadLocalRepoResources
import org.eclipse.jgit.lib.Constants._

class BlobRemover(blobIds: Set[ObjectId]) extends Cleaner[TreeBlobs] {
  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.filter(e => !blobIds.contains(e.objectId))
}

class BlobReplacer(badBlobs: Set[ObjectId], blobInserter: => BlobInserter) extends Cleaner[TreeBlobs] {
  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.map {
    case e if badBlobs.contains(e.objectId) =>
      TreeBlobEntry(FileName(e.filename + ".REMOVED.git-id"), RegularFile, blobInserter.insert(e.objectId.name.getBytes))
    case e => e
  }
}

trait TreeBlobModifier extends Cleaner[TreeBlobs] {

  val memo: Memo[TreeBlobEntry, TreeBlobEntry] = MemoUtil.concurrentCleanerMemo(Set.empty)

  val memoisedCleaner: (TreeBlobEntry) => TreeBlobEntry = memo {
    entry =>
      val (mode, objectId) = fix(entry)
      TreeBlobEntry(entry.filename, mode, objectId)
  }

  override def apply(treeBlobs: TreeBlobs) = treeBlobs.entries.map(memoisedCleaner)

  def fix(entry: TreeBlobEntry): (BlobFileMode, ObjectId) // implementing code can not safely know valid filename
}

trait BlobCharsetDetector {
  // should return None if this is a binary file that can not be converted to text
  def charsetFor(entry: TreeBlobEntry, streamResource: InputStreamResource[ObjectStream]): Option[Charset]
}


object QuickBlobCharsetDetector extends BlobCharsetDetector {

  val charSets = Seq(Charset.forName("UTF-8"), Charset.defaultCharset(), Charset.forName("ISO-8859-1")).distinct

  def charsetFor(entry: TreeBlobEntry, streamResource: InputStreamResource[ObjectStream]): Option[Charset] =
    Some(streamResource.bytes.take(8000).toArray).filterNot(RawText.isBinary).flatMap {
      sampleBytes =>
        val b = ByteBuffer.wrap(sampleBytes)
        charSets.find(cs => Try(decode(b, cs)).isSuccess)
    }

  private def decode(b: ByteBuffer, charset: Charset) {
    charset.newDecoder.onMalformedInput(REPORT).onUnmappableCharacter(REPORT).decode(b)
  }
}

object BlobTextModifier {

  val DefaultSizeThreshold = 1024 * 1024

}

trait BlobTextModifier extends TreeBlobModifier {

  val threadLocalRepoResources: ThreadLocalRepoResources

  def lineCleanerFor(entry: TreeBlobEntry): Option[String => String]

  val charsetDetector: BlobCharsetDetector

  val sizeThreshold = DefaultSizeThreshold

  override def fix(entry: TreeBlobEntry) = {

    def filterTextIn(e: TreeBlobEntry, lineCleaner: String => String): TreeBlobEntry = {
      def isDirty(line: String) = lineCleaner(line) != line

      Some(threadLocalRepoResources.objectReader().open(e.objectId)).filter(_.getSize < sizeThreshold).flatMap {
        loader =>
          Some(Resource.fromInputStream(loader.openStream())).flatMap {
            streamResource =>
              charsetDetector.charsetFor(e, streamResource).flatMap {
                charset =>
                  Some(streamResource.reader(charset)).map(_.lines(includeTerminator = true)).filter(_.exists(isDirty)).map {
                    lines =>
                      val b = new ByteArrayOutputStream(loader.getSize.toInt)

                      lines.view.map(lineCleaner).foreach(line => b.write(line.getBytes(charset)))

                      val oid = threadLocalRepoResources.objectInserter().insert(OBJ_BLOB, b.toByteArray)

                      e.copy(objectId = oid)
                  }
              }
          }
      }.getOrElse(e)
    }

    lineCleanerFor(entry) match {
      case Some(lineCleaner) => filterTextIn(entry, lineCleaner).withoutName
      case None => entry.withoutName
    }
  }
}