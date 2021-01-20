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

import java.io.{ByteArrayOutputStream, InputStream, Reader}
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

  /*
  https://github.com/google/guava/commit/a2c7f54378dc2585f8524f59d71e56353ac0a1ba
  Usually a line - multiple lines - will fit into the character buffer.
  Occasionally a line could be crazy long, and span multiple lengths of the buffer.
  LF, CR, CR LF (windows) - but disregard LF CR (Acorn)
   */
  def wrapReader(reader: Reader, bufferSize: Int = 0x800): Iterator[String] = new Iterator[String] {
    val buf = new Array[Char](bufferSize)

    var endOfStream: Boolean = false

    /**
     * Anything from `readPointer` onwards, up to `writePointer` exclusive,
     * can be read.
     *
     * After a line is read, `readPointer` will be pointing to the next character
     * immediately after the terminator of that line.
     */
    var readPointer: Int = 0

    /**
     * Anything from `writePointer` onwards, up to `readPointer` exclusive,
     * can be overwritten.
     */
    var writePointer: Int = 0

    private def readableBufferedBytes: Int = if (readPointer <= writePointer) writePointer - readPointer else {
      writePointer + (buf.length - readPointer)
    }

    private def hasReadableBufferedBytes = readPointer != writePointer // check assumptions here - do we use sentinel values?

    /**
     * @param endExclusive - may be writePointer, or just the end of a line
     */
    private def slurp(sb: StringBuilder, endExclusive: Int): Unit = {
      if (readPointer > endExclusive) {
        sb.appendAll(buf, readPointer, buf.length - readPointer)
      }
      sb.appendAll(buf, 0, endExclusive)
      readPointer = endExclusive
    }

    override def hasNext: Boolean = hasReadableBufferedBytes || !endOfStream

    private def numBytesWeCouldAcceptInOneRead: Int = {
      val firstUnwritableIndex = if (readPointer > writePointer) readPointer else buf.length
      firstUnwritableIndex - writePointer
    }

    /*
     * Must repeatedly fill until it finds a newline or the endOfStream
     *
     */
    override def next(): String = {
      // search readableBufferedBytes for line-break
      // if found, return string up to and including line-break characters, advance readPointer
      // if not found:
      //   is numBytesWeCouldAcceptInOneRead==0?
      //     YES: then we must start a StringBuilder
      //     NO: fill(), then go back to start
      //

      // easy/quick case - find a line-break between readPointer and BufferEnd, no need for StringBuilder
      // ...drops into we can NOT find a line-break between readPointer and BufferEnd, we MUST use a StringBuilder


      ???
    }

    def findLineBreak(): Unit = {
      var i=readPointer
      while (i < writePointer) {

      }
        buf.indexWhere(c => c == '\n' || c == '\r', readPointer)
      }
    }


    private def findLineAcrossBufferEdge(): String = {
      val sb = new StringBuilder
      sb.appendAll(buf, readPointer, buf.length - readPointer)
      readPointer = 0
      do {
        fill()


      } while ()
      // fill, search
      // line-break found or allStreamConsumed?
      // YES: append up to line-break/stream-end, advance readPointer, terminate
      // NO: append all up to writePointer, advance readPointer to writePointer, repeat loop
      sb.toString
    }

      //      while (readableBufferedBytes > 0) {
      //
      //      } else {
      //        while(!eol) {
      //          fill()
      //        }
      //
      //      }
      //
      //      val sb = new StringBuilder


    def findNewline(): Unit = {
//      while (readPointer < writePointer && buf[readPointer])
//        buf.indexWhere(c => c == '\n' || c == '\r', readPointer)
    }



    private def fill(): Unit = {
      val numBytesToAttemptToRead = numBytesWeCouldAcceptInOneRead
      if (numBytesToAttemptToRead > 0) {
        val bytesRead = reader.read(buf, writePointer, numBytesToAttemptToRead)
        if (bytesRead == -1) {
          endOfStream = true
        } else {
          writePointer = (writePointer + bytesRead) % buf.length
        }
      }
    }

    private def copyReaderToBuilder(reader: Reader, to: StringBuilder) = {
    checkNotNull(reader)
    checkNotNull(to)
    val buf = new Array[Char](0x800)
    var nRead = 0
    var total = 0
    while ({
      nRead = reader.read(buf)
      nRead
    } != -1) {
      to.append(buf, 0, nRead)
      total += nRead
    }
    total
  }


  def wrappy(inputStream: InputStream, charset: Charset): Iterable[String] = new Iterable[String] {
    override def iterator: Iterator[String] = new Iterator[String] {

      val scanner = {
        val s = new Scanner(inputStream, charset.name())
        s.useDelimiter(Pattern.compile("\\R|\\z"))
        s
      }

      var hasN = true

      override def hasNext: Boolean = scanner.hasNext(Pattern.compile("\\R|\\z"))

      override def next(): String = {
        val str = scanner.findWithinHorizon(pat, 0)
        println(s"found:$str")
        hasN = str == null
        str
      }
    }
  }
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
        charset <- charsetDetector.charsetFor(e, loader) if loader.getSize < sizeThreshold
        lines: Iterable[String] = BlobTextModifier.wrappy(loader.openStream(), charset) if lines.exists(isDirty)
      } yield {
        val dirty = new String(loader.getBytes)
        val b = new ByteArrayOutputStream(loader.getSize.toInt)
        lines.map(lineCleaner).foreach(line => b.write(line.getBytes(charset)))
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
}
