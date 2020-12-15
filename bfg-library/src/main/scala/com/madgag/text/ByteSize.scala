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

object ByteSize {

  import math._

  val magnitudeChars = Seq('B', 'K', 'M', 'G', 'T', 'P')
  val unit = 1024

  def parse(v: String): Long = magnitudeChars.indexOf(v.takeRight(1)(0).toUpper) match {
    case -1 => throw new IllegalArgumentException(s"Size unit is missing (ie ${magnitudeChars.mkString(", ")})")
    case index => v.dropRight(1).toLong << (index * 10)
  }

  def format(bytes: Long): String = {
    if (bytes < unit) s"$bytes B " else {
      val exp = (log(bytes.toDouble) / log(unit)).toInt
      val pre = magnitudeChars(exp)
      "%.1f %sB".format(bytes / pow(unit, exp), pre)
    }
  }

}
