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

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.io.{ByteSink, ByteSource, ByteStreams, CharSink, CharStreams}
import com.madgag.git.bfg.model._

import java.io.{ByteArrayOutputStream, InputStream, InputStreamReader, Reader}
import com.madgag.git.ThreadLocalObjectDatabaseResources
import com.madgag.git.bfg.model.TreeBlobEntry
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import org.eclipse.jgit.lib.ObjectLoader

import java.nio.charset.Charset
import java.util.Scanner
import java.util.regex.Pattern
import scala.jdk.CollectionConverters.IterableHasAsJava


object BlobTextModifier {

  val DefaultSizeThreshold: Long = 1024 * 1024

  val pat: Pattern = Pattern.compile(".*\\R|.+\\z")
  //val pat: Pattern = Pattern.compile(".*?\\R")

}

trait BlobTextModifier extends TreeBlobModifier {

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def lineCleanerFor(entry: TreeBlobEntry): Option[String => String]

  val charsetDetector: BlobCharsetDetector = QuickBlobCharsetDetector

  val sizeThreshold = BlobTextModifier.DefaultSizeThreshold

  override def fix(entry: TreeBlobEntry) = {

    def filterTextIn(e: TreeBlobEntry, lineCleaner: String => String): TreeBlobEntry = {
      def isDirty(line: String) = lineCleaner(line) != line

      val loader = threadLocalObjectDBResources.reader().open(e.objectId)
      val opt = for {
        charset <- charsetDetector.charsetFor(e, loader)
        if loader.getSize < sizeThreshold && linesFor(loader, charset).exists(isDirty)
      } yield {
        val b = new ByteArrayOutputStream(loader.getSize.toInt)
        linesFor(loader, charset).map(lineCleaner).foreach(line => b.write(line.getBytes(charset)))
        val oid = threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, b.toByteArray)
        e.copy(objectId = oid)
      }

      opt.getOrElse(e)
    }

    lineCleanerFor(entry) match {
      case Some(lineCleaner) => filterTextIn(entry, lineCleaner).withoutName
      case None => entry.withoutName
    }
  }

  private def linesFor(loader: ObjectLoader, charset: Charset) = {
    new LineBreakInclusiveIterator(new InputStreamReader(loader.openStream(), charset))
  }
}
