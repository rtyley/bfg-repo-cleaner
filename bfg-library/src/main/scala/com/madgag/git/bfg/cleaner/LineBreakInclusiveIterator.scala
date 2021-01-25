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

import java.io.Reader
import scala.annotation.tailrec
import scala.util.matching.Regex

case class FillResult(filledToBufferEdge: Boolean, endOfStream: Boolean)

/*
  https://github.com/google/guava/commit/a2c7f54378dc2585f8524f59d71e56353ac0a1ba
  Usually a line - multiple lines - will fit into the character buffer.
  Occasionally a line could be crazy long, and span multiple lengths of the buffer.
  LF, CR, CR LF (windows) - but disregard LF CR (Acorn)
  \n, \r, \r \n
   */
class LineBreakInclusiveIterator(reader: Reader, bufferSize: Int = 0x800) extends Iterator[String] {
  private val lineBreak: Regex = "\\R".r

  val MaxLBSize = 2

  require(bufferSize >= MaxLBSize + 1)

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
    val foundLine:Option[String] = findLBX() // case A "LBX visible in readable bytes before buffer edge"
    val result = foundLine.getOrElse {
      if (readPointer <= writePointer) caseB_readBeforeOrEqualToWritePointer() else caseC_writeBeforeReadPointer()
    }

    val lineBreakDistanceBeforeEndOfString = lineBreak.findAllMatchIn(result).map(m => result.length - m.end).toSeq
    assert(lineBreakDistanceBeforeEndOfString.forall(_ == 0), (Seq(
      s"'$result' should have at most 1 line break, and if it exists that line-break should be at the end of the string."
    ) ++ Option.when(lineBreakDistanceBeforeEndOfString.nonEmpty)(s"Line-breaks occur at ${lineBreakDistanceBeforeEndOfString.mkString(", ")} char before the end of the string")).mkString(" "))

    result
  }

  // case C "read is before or equal to write in the buffer, with no LBX visible in readable bytes"
  // @tailrec
  private def caseB_readBeforeOrEqualToWritePointer(): String = {
    val fillResult = fill()
    if (fillResult.endOfStream) {
      val str = new String(buf, readPointer, writePointer - readPointer)
      readPointer = writePointer
      str
    } else {
      val fl: Option[String] = findLBX()
      fl.getOrElse {
        if (fillResult.filledToBufferEdge) caseC_writeBeforeReadPointer() else caseB_readBeforeOrEqualToWritePointer()
      }
    }
  }

  private def startStringBuilderAndLoopReadPointerToBufferStart(): StringBuilder = {
    val stringBuilder = new StringBuilder()
    loopRound(stringBuilder)
    stringBuilder
  }

  private def loopRound(stringBuilder: StringBuilder): Unit = {
    val charsRemaining = buf.length - readPointer
    val numCharsToClone = Math.min(MaxLBSize, charsRemaining)
    stringBuilder.appendAll(buf, readPointer, charsRemaining - numCharsToClone)
    Array.copy(buf, buf.length - numCharsToClone, buf, 0, numCharsToClone)
    readPointer = 0
    writePointer = numCharsToClone
  }

  // case C "read is ahead of write in the buffer, with no LBX visible before buffer edge"
  private def caseC_writeBeforeReadPointer(): String = {
    stringBuilderSearching(startStringBuilderAndLoopReadPointerToBufferStart())
  }

  private def grabUpTo(startOfNextLine: Int): String = {
    val str = new String(buf, readPointer, startOfNextLine - readPointer)
    readPointer = startOfNextLine
    str
  }

  def findLBX(): Option[String] = {
    var i = readPointer
    val searchBoundary = (if (writePointer==0) buf.length else writePointer) - 1
    val boundaryFor2CharLB = searchBoundary - 1

    while (i < searchBoundary) {
      val c = buf(i)
      if (c == '\r' && buf(i+1) == '\n') {
        return if (i < boundaryFor2CharLB) Some(grabUpTo(i + 2)) else None // Need to ensure there's a buffer byte...
      } else if (c == '\n' || c == '\r') {
        return Some(grabUpTo(i + 1))
      }
      i += 1
    }
    None
  }

  /*
  stringBuilder has been checked for LBX, but may contain full or partial LB at the tail
   */
  def findLBXWith(stringBuilder: StringBuilder): Option[String] = {
    var i = 0
    val searchBoundary = (if (writePointer==0) buf.length else writePointer) - 1
    val boundaryFor2CharLB = searchBoundary - 1

    while (i < searchBoundary) {
      val c = buf(i)
      if (c == '\r' && buf(i+1) == '\n') {
        return if (i < boundaryFor2CharLB) Some(stringBuilder.append(grabUpTo(i + 2)).result) else None // Need to ensure there's a buffer byte...
      } else if (c == '\n' || c == '\r') {
        return Some(stringBuilder.append(grabUpTo(i + 1)).result)
      }
      i += 1
    }
    None
  }

  def stringBuilderSearching(stringBuilder: StringBuilder): String = {
    val fillResult = fill()
    if (fillResult.endOfStream) {
      stringBuilder.appendAll(buf, readPointer, writePointer - readPointer)
      readPointer = writePointer
      stringBuilder.result
    } else {
      val fl: Option[String] = findLBXWith(stringBuilder)
      fl.getOrElse {
        if (fillResult.filledToBufferEdge) {
          loopRound(stringBuilder)
        }
        stringBuilderSearching(stringBuilder)
      }
    }
  }

  def findLineBreak(): Option[Int] = {
    var i = readPointer
    while (i < writePointer - 1) {
      val c = buf(i)
      if(c == '\n' || c == '\r') return Some(i)
      i += 1
    }
    None
  }

  /*
  Fill needs to block/loop until it either reaches endOfStream or reads at least one byte
   */
  @tailrec
  private def fill(): FillResult = {
    val bytesRead = reader.read(buf, writePointer, numBytesWeCouldAcceptInOneRead)
    bytesRead match {
      case -1 =>
        endOfStream = true
        FillResult(filledToBufferEdge=false, endOfStream = true)
      case 0 =>
        fill()
      case _ =>
        writePointer = (writePointer + bytesRead) % buf.length // if we filled the buf, writePointer goes back to zero
        FillResult(filledToBufferEdge = writePointer == 0, endOfStream = false)
    }
  }

}
