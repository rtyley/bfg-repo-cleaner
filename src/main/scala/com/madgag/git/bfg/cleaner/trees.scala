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

import org.eclipse.jgit.lib.{ObjectStream, ObjectDatabase, ObjectId}
import com.madgag.git.bfg.model._
import org.eclipse.jgit.diff.RawText
import java.io._
import org.eclipse.jgit.lib.Constants._
import com.madgag.git.bfg.cleaner.TreeBlobsCleaner.Kit
import scalaz.Memo
import com.madgag.git.bfg.MemoUtil
import scalax.io.Resource
import java.nio.charset.Charset
import scala.Some
import com.madgag.git.bfg.model.TreeBlobEntry
import BlobTextModifier._
import scalax.io.managed.InputStreamResource
import java.nio.ByteBuffer
import util.Try
import java.nio.charset.CodingErrorAction._

object TreeBlobsCleaner {

  class Kit(objectDB: ObjectDatabase) {
    lazy val objectReader = objectDB.newReader

    private lazy val inserter = objectDB.newInserter

    lazy val blobInserter = new BlobInserter {
      def insert(length: Long, in: InputStream) = inserter.insert(OBJ_BLOB, length, in)

      def insert(data: Array[Byte]) = inserter.insert(OBJ_BLOB, data)
    }
  }

  def chain(cleaners: Seq[TreeBlobsCleaner]) = new TreeBlobsCleaner {
    override def fixer(kit: TreeBlobsCleaner.Kit) = Function.chain(cleaners.map(_.fixer(kit)))
  }
}

trait TreeBlobsCleaner {
  def fixer(kit: TreeBlobsCleaner.Kit): Cleaner[TreeBlobs]
}

class BlobRemover(blobIds: Set[ObjectId]) extends TreeBlobsCleaner {
  override def fixer(kit: Kit) = _.entries.filter(e => !blobIds.contains(e.objectId))
}

class BlobReplacer(badBlobs: Set[ObjectId]) extends TreeBlobsCleaner {
  def fixer(kit: Kit) = _.entries.map {
    case e if badBlobs.contains(e.objectId) =>
      TreeBlobEntry(FileName(e.filename + ".REMOVED.git-id"), RegularFile, kit.blobInserter.insert(e.objectId.name.getBytes))
    case e => e
  }
}

trait TreeBlobModifier extends TreeBlobsCleaner {

  val memo: Memo[TreeBlobEntry, TreeBlobEntry] = MemoUtil.concurrentCleanerMemo(Set.empty)

  override def fixer(kit: Kit) = _.entries.map(memo {
    entry =>
      val (mode, objectId) = fix(entry, kit)
      TreeBlobEntry(entry.filename, mode, objectId)
  })

  def fix(entry: TreeBlobEntry, kit: Kit): (BlobFileMode, ObjectId) // implementing code can not safely know valid filename
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

  def lineCleanerFor(entry: TreeBlobEntry): Option[String => String]

  val charsetDetector: BlobCharsetDetector

  val sizeThreshold = DefaultSizeThreshold

  override def fix(entry: TreeBlobEntry, kit: Kit) = {

    def filterTextIn(e: TreeBlobEntry, lineCleaner: String => String): TreeBlobEntry = {
      def isDirty(line: String) = lineCleaner(line) != line

      Some(kit.objectReader.open(e.objectId)).filter(_.getSize < sizeThreshold).flatMap {
        loader =>
          Some(Resource.fromInputStream(loader.openStream())).flatMap {
            streamResource =>
              charsetDetector.charsetFor(e, streamResource).flatMap {
                charset =>
                  Some(streamResource.reader(charset)).map(_.lines(includeTerminator = true)).filter(_.exists(isDirty)).map {
                    lines =>
                      val b = new ByteArrayOutputStream(loader.getSize.toInt)

                      lines.view.map(lineCleaner).foreach(line => b.write(line.getBytes(charset)))

                      val oid = kit.blobInserter.insert(b.toByteArray)

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