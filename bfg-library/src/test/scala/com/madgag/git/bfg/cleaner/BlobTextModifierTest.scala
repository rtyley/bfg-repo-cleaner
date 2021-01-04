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

import com.madgag.git.bfg.cleaner.BlobTextModifier.wrappy
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8

class BlobTextModifierTest extends AnyFlatSpec with Matchers {
  def splittingLinesOf(text: String) = {
    val textStream = new ByteArrayInputStream(text.getBytes(UTF_8))

    val lines: Seq[String] = wrappy(textStream, UTF_8).toSeq
    lines.foreach(x => println(s"x:$x"))
    lines.mkString shouldBe text
  }

  it should "split on Windows newlines" in splittingLinesOf("Foo\r\n\r\nMoo")
  it should "split on UNIX newlines" in splittingLinesOf("Bar\n\nBoo")
  it should "split on Windows newlines at the end of the data" in splittingLinesOf("Foo\r\n\r\n")
  it should "split on UNIX newlines at the end of the data" in splittingLinesOf("Bar\n\n")

}
