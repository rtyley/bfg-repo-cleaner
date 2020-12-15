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

package com.madgag.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ByteSizeSpecs extends AnyFlatSpec with Matchers {
  "Size parser" should "understand 1B" in {
    ByteSize.parse("0B") shouldBe 0
    ByteSize.parse("1B") shouldBe 1
    ByteSize.parse("2B") shouldBe 2
    ByteSize.parse("10B") shouldBe 10
  }
  it should "understand 3G" in {
    ByteSize.parse("3G") shouldBe 3L * 1024 * 1024 * 1024
  }
  it should "understand 1G" in {
    ByteSize.parse("1G") shouldBe 1024 * 1024 * 1024
  }
  it should "understand 1M" in {
    ByteSize.parse("1M") shouldBe 1024 * 1024
  }
  it should "understand 3500M" in {
    ByteSize.parse("3500M") shouldBe 3500L * 1024 * 1024
  }
  it should "understand 1K" in {
    ByteSize.parse("1K") shouldBe 1024
  }
  it should "understand 5K" in {
    ByteSize.parse("5K") shouldBe 5 * 1024
  }
  it should "reject strings without a unit" in {
    an[IllegalArgumentException] should be thrownBy ByteSize.parse("1232")
  }

  "Size formatter" should "correctly format" in {
    ByteSize.format(1024) shouldBe "1.0 KB"
  }
}
