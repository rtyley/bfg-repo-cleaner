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

object Tables {
  def formatTable(header: Product, data: Seq[Product], maxDataRows: Int = 16): Seq[String] = {
    val numColumns = data.head.productArity
    val sizes: Seq[Int] = (0 until numColumns).map(i => (data :+ header).map(_.productElement(i).toString.length).max)
    def padLine(l: Product): IndexedSeq[String] = {
      (0 until numColumns).map(c => l.productElement(c).toString.padTo(sizes(c), ' '))
    }

    val headerLine = padLine(header).mkString("   ")
    Text.abbreviate(headerLine +: "-" * headerLine.size +: data.map {
      l =>
        padLine(l).mkString(" | ")
    }, "...", maxDataRows+2).toSeq
  }
}
