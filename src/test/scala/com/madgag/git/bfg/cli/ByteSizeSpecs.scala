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

package com.madgag.git.bfg.cli

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.madgag.git.bfg.model.FileName

class ByteSizeSpecs extends FlatSpec with ShouldMatchers {
  "Size parser" should "understand 1M" in {
    ByteSize.parse("1M") should be(1024 * 1024)
  }

  "Size parser" should "understand 1K" in {
    ByteSize.parse("1K") should be(1024)
  }

  "Size formatter" should "correctly format" in {
    ByteSize.format(1024) should be("1.0 KB")
  }
}
