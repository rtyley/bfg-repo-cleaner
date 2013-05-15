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

package com.madgag.git.bfg.model

import org.eclipse.jgit.lib._
import org.eclipse.jgit.treewalk.CanonicalTreeParser

import Constants.{OBJ_BLOB, OBJ_TREE}
import scala.collection

object Tree {

  val Empty = Tree(Map.empty[FileName, (FileMode, ObjectId)])

  def apply(entries: Traversable[Tree.Entry]): Tree = Tree(entries.map {
    entry => entry.name ->(entry.fileMode, entry.objectId)
  }.toMap)

  def apply(objectId: ObjectId)(implicit objectReader: ObjectReader): Tree = {
    val treeParser = new CanonicalTreeParser
    treeParser.reset(objectReader, objectId)
    val entries = collection.mutable.Buffer[Entry]()
    while (!treeParser.eof) {
      entries += Entry(treeParser)
      treeParser.next()
    }
    Tree(entries)
  }

  case class Entry(name: FileName, fileMode: FileMode, objectId: ObjectId) extends Ordered[Entry] {

    def compare(that: Entry) = pathCompare(name.bytes, that.name.bytes, fileMode, that.fileMode)

    private def pathCompare(a: Array[Byte], b: Array[Byte], aMode: FileMode, bMode: FileMode): Int = {

      def lastPathChar(mode: FileMode): Int = if ((FileMode.TREE == mode)) '/' else '\0'

      var pos = 0
      while (pos < a.length && pos < b.length) {
        val cmp: Int = (a(pos) & 0xff) - (b(pos) & 0xff)
        if (cmp != 0) return cmp
        pos += 1
      }

      if (pos < a.length) {
        (a(pos) & 0xff) - lastPathChar(bMode)
      } else if (pos < b.length) {
        lastPathChar(aMode) - (b(pos) & 0xff)
      } else {
        0
      }
    }
  }

  object Entry {

    def apply(treeParser: CanonicalTreeParser): Entry = {
      val nameBuff = new Array[Byte](treeParser.getNameLength)
      treeParser.getName(nameBuff, 0)

      Entry(new FileName(nameBuff), treeParser.getEntryFileMode, treeParser.getEntryObjectId)
    }

  }

  trait EntryGrouping {
    val treeEntries: Traversable[Tree.Entry]
  }

}

case class Tree(entryMap: Map[FileName, (FileMode, ObjectId)]) {

  protected def repr = this

  lazy val entries = entryMap.map {
    case (name, (fileMode, objectId)) => Tree.Entry(name, fileMode, objectId)
  }

  lazy val entriesByType = entries.groupBy(_.fileMode.getObjectType).withDefaultValue(Seq.empty)

  lazy val sortedEntries = entries.toList.sorted

  def formatter: TreeFormatter = {
    val treeFormatter = new TreeFormatter()
    sortedEntries.foreach(e => treeFormatter.append(e.name.bytes, e.fileMode, e.objectId))

    treeFormatter
  }

  lazy val objectId = formatter.computeId(new ObjectInserter.Formatter)

  lazy val blobs = TreeBlobs(entriesByType(OBJ_BLOB).flatMap {
    e => BlobFileMode(e.fileMode).map {
      blobFileMode => e.name ->(blobFileMode, e.objectId)
    }
  }.toMap)

  lazy val subtrees = TreeSubtrees(entriesByType(OBJ_TREE).map {
    e => e.name -> e.objectId
  }.toMap)

  def copyWith(subtrees: TreeSubtrees, blobs: TreeBlobs): Tree = {
    val otherEntries = (entriesByType - OBJ_BLOB - OBJ_TREE).values.flatten
    Tree(blobs.treeEntries ++ subtrees.treeEntries ++ otherEntries)
  }

}

case class TreeBlobEntry(filename: FileName, mode: BlobFileMode, objectId: ObjectId) {
  lazy val toTreeEntry = Tree.Entry(filename, mode.mode, objectId)

  lazy val withoutName: (BlobFileMode, ObjectId) = (mode, objectId)
}

object TreeBlobs {
  import language.implicitConversions

  implicit def entries2Object(entries: Traversable[TreeBlobEntry]) = TreeBlobs(entries)

  def apply(entries: Traversable[TreeBlobEntry]): TreeBlobs =
    TreeBlobs(entries.map(e => e.filename ->(e.mode, e.objectId)).toMap)
}

case class TreeBlobs(entryMap: Map[FileName, (BlobFileMode, ObjectId)]) extends Tree.EntryGrouping {

  lazy val entries = entryMap.map {
    case (name, (blobFileMode, objectId)) => TreeBlobEntry(name, blobFileMode, objectId)
  }

  lazy val treeEntries = entries.map(_.toTreeEntry)
  //
  //  def filter(p: ObjectId => Boolean): TreeBlobs = {
  //    TreeBlobs(entries.filter {
  //      case TreeBlobEntry(_, _ , objectId) => p(objectId)
  //    })
  //  }

}

case class TreeSubtrees(entryMap: Map[FileName, ObjectId]) extends Tree.EntryGrouping {

  lazy val treeEntries = entryMap.map {
    case (name, objectId) => Tree.Entry(name, FileMode.TREE, objectId)
  }

  lazy val withoutEmptyTrees = TreeSubtrees(entryMap.filterNot(_._2 == Tree.Empty.objectId))
}