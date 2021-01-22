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
import org.scalatest.Inspectors._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.matching.Regex

class LineBreakInclusiveIteratorTest extends AnyFlatSpec with Matchers {

  val lineBreak: Regex = "\\R".r

  def splittingLinesOf(text: String): Assertion = {
    val textStream = new ByteArrayInputStream(text.getBytes(UTF_8))

    forAll(2 to text.length + 1) { bufferSize =>
      val lines: Seq[String] = new LineBreakInclusiveIterator(new InputStreamReader(textStream), bufferSize).toSeq

      lines.mkString shouldBe text

      forAll(lines.dropRight(1)) { line =>
        line should endWith regex lineBreak
        lineBreak.findAllIn(line) should have length 1
      }
      lineBreak.findAllIn(lines.last).size should be <= 1
    }

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


  def boof(text: String): Seq[String] = (new LineBreakInclusiveIterator(readerFor(text))).toSeq

  def readerFor(text: String): Reader = new InputStreamReader(new ByteArrayInputStream(text.getBytes(UTF_8)))

  class PathologicalStringReader(segments: Seq[String]) extends Reader {
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
