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

import com.madgag.git.bfg.cleaner.BlobTextModifier._
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.StringEscapeUtils.escapeJava
import org.scalatest.Inspectors._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.regex.Pattern
import scala.collection.BitSet
import scala.util.matching.Regex

class LineBreakInclusiveIteratorTest extends AnyFlatSpec with Matchers with OptionValues {

  val lineBreak: Regex = "\\R".r

  def splittingLinesOf(text: String) = {
    forAll(3 to text.length + 1) { bufferSize =>
      testThis(text, new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8))), bufferSize)

      for (reader <- PathologicalStringReader.allPossible(text)) {
        // testThis(text, reader, bufferSize)
      }
    }
  }

  def testThis(text: String, reader: Reader, bufferSize: Int): Unit = {
    val lines: Seq[String] = new LineBreakInclusiveIterator(reader, bufferSize).toSeq

    lines.mkString shouldBe text

    forAll(lines.dropRight(1)) { line =>
      val lineWithEscapedLineBreaks = lineBreak.replaceAllIn(line, m => escapeJava(escapeJava(m.matched)))
      assert(lineBreak.findAllIn(line).toSeq.size == 1, s": '$lineWithEscapedLineBreaks' should have exactly 1 line break")
    }
    lineBreak.findAllIn(lines.last).size should be <= 1
  }

  it should "handle the empty string" in splittingLinesOf("")
  it should "handle a simple string with no newlines" in splittingLinesOf("foo")
  it should "split on Windows newlines" in splittingLinesOf("Foo\r\n\r\nMoo")
  it should "split on UNIX newlines" in splittingLinesOf("Bar\n\nBoo")
  it should "split on Windows newlines at the end of the data" in splittingLinesOf("Foo\r\n\r\n")
  it should "split on UNIX newlines at the end of the data" in splittingLinesOf("Bar\n\n")

  it should "return only one line (empty!) for the empty string" in {
    boof("") shouldBe Seq("")
  }

  it should "return 1 line for a simple string" in {
    boof("foo") shouldBe Seq("foo")
  }

  it should "return 1 line for a simple string with a newline at it's end" in {
    boof("foo\n") shouldBe Seq("foo\n")
  }

  it should "return 2 lines for a simple string with a newline in the middle of it" in {
    boof("foo\nbar") shouldBe Seq("foo\n","bar")
  }

  it should "return 2 lines for two simple strings each ending with a newline" in {
    boof("foo\nbar\n") shouldBe Seq("foo\n","bar\n")
  }

  it should "handle weird border issues" in {
    boof("h\r\n\r\nmno", bufferSize = 3) shouldBe Seq("h\r\n", "\r\n", "mno")
  }

  it should "handle weird border issues quicka" in {
    boof("mno", bufferSize = 3) shouldBe Seq("mno")
  }

  it should "handle crazy thang" in {
    boof("foo", bufferSize = 3) shouldBe Seq("foo")
  }

  it should "be cool, real cool" in {
    boof("F\r\n\r\nM", bufferSize = 5) shouldBe Seq("F\r\n", "\r\n", "M")
  }

  it should "be cool, fool" in {
    boof("F\n\nM", bufferSize = 3) shouldBe Seq("F\n", "\n", "M")
  }

  it should "be super cool" in {
    new LineBreakInclusiveIterator(new PathologicalStringReader(Seq("\r", "\n"))).toSeq shouldBe Seq("\r\n")
  }


  it should "be sensible about how many separate lines you get" in {
    boof("\n") shouldBe Seq("\n")
    boof("\n\n") shouldBe Seq("\n", "\n")
    boof("\r") shouldBe Seq("\r")
    boof("\r\r") shouldBe Seq("\r", "\r")
    boof("\r\n") shouldBe Seq("\r\n")
    boof("\r\n\r\n") shouldBe Seq("\r\n", "\r\n")
    boof("\r\r\n\r") shouldBe Seq("\r", "\r\n", "\r")
  }


  def boof(text: String, bufferSize: Int = 1024): Seq[String] =
    new LineBreakInclusiveIterator(readerFor(text), bufferSize).toSeq

  def readerFor(text: String): Reader = new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)))

  object PathologicalStringReader {
    def allPossible(text: String): Iterable[PathologicalStringReader] = {
      require(text.length <= 9)
      for (i <- 0 until 1 << text.length) yield {
        val breakIndicies = BitSet(i).to(Seq)
        val segments: Seq[String] =
          breakIndicies.zip(breakIndicies.tail).map { case (start, end) => text.substring(start, end) }
        new PathologicalStringReader(segments)
      }
    }
  }

  class PathologicalStringReader(val segments: Seq[String]) extends Reader {
    var closed = false

    var currentSegmentNumber = 0
    var currentProgressWithinSegment = 0

    override def read(cbuf: Array[Char], off: Int, len: Int): Int = {
      require(!closed)
      if (currentSegmentNumber >= segments.length) -1 else {
        val segment = segments(currentSegmentNumber)
        val remainingSegment: String = segment.drop(currentProgressWithinSegment)
        val lenToCopy = Math.min(remainingSegment.length, len)
        val segmentToGive = remainingSegment.take(lenToCopy)
        Array.copy(segmentToGive.toCharArray, 0, cbuf, off, lenToCopy)
        currentProgressWithinSegment += lenToCopy
        if (currentProgressWithinSegment == segment.length) {
          currentSegmentNumber += 1
          currentProgressWithinSegment = 0
        }
        lenToCopy
      }
    }

    override def close(): Unit = {
      closed = true
    }
  }

}
