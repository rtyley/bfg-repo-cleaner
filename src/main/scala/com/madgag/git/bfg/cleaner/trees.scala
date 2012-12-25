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

import org.eclipse.jgit.lib.{ObjectDatabase, ObjectId}
import com.madgag.git.bfg.model.{RegularFile, FileName, TreeBlobs}
import org.eclipse.jgit.diff.RawText
import java.io.{InputStream, ByteArrayOutputStream}
import org.eclipse.jgit.lib.Constants._
import com.madgag.git.bfg.cleaner.TreeCleaner.Kit


object TreeCleaner {

  class Kit(objectDB: ObjectDatabase) {
    lazy val objectReader = objectDB.newReader

    private lazy val inserter = objectDB.newInserter

    lazy val blobInserter = new BlobInserter {
      def insert(length: Long, in: InputStream) = inserter.insert(OBJ_BLOB, length, in)

      def insert(data: Array[Byte]) = inserter.insert(OBJ_BLOB, data)
    }
  }

}

trait TreeCleaner {
  def fix(treeBlobs: TreeBlobs, kit: Kit): TreeBlobs
}

class BlobRemover(blobIds: Set[ObjectId]) extends TreeCleaner {
  def fix(treeBlobs: TreeBlobs, kit: Kit) = treeBlobs.filter(oid => !blobIds.contains(oid))
}

class BlobReplacer(badBlobs: Set[ObjectId]) extends TreeCleaner {
  def fix(treeBlobs: TreeBlobs, kit: Kit) = {
    val updatedEntryMap = treeBlobs.entryMap.map {
      case (filename, (mode, oid)) if badBlobs.contains(oid) =>
        FileName(filename + ".REMOVED.git-id") ->(RegularFile, kit.blobInserter.insert(oid.name.getBytes))
      case e => e
    }
    TreeBlobs(updatedEntryMap)
  }
}

class BlobPasswordRemover extends BlobTextModifier {
  val reg = """(\.password=).*""".r

  override def cleanLine(line: String) = reg.replaceAllIn(line, m => m.group(1) + "*** PASSWORD ***")
}

trait BlobTextModifier extends TreeCleaner {

  def cleanLine(line: String): String

  def fix(treeBlobs: TreeBlobs, kit: Kit) = {

    TreeBlobs(treeBlobs.entries.map {
      e =>
        val objectLoader = kit.objectReader.open(e.objectId)
        if (objectLoader.isLarge) {
          println("LARGE")
          e
        } else {
          val cachedBytes = objectLoader.getCachedBytes
          val rawText = new RawText(cachedBytes)

          val b = new ByteArrayOutputStream(cachedBytes.length)

          (1 until rawText.size).map(rawText.getString(_)).map(cleanLine).foreach(line => b.write(line.getBytes))

          val oid = kit.blobInserter.insert(b.toByteArray)

          e.copy(objectId = oid)
        }
    })
  }
}