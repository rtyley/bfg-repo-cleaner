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

import com.madgag.git.bfg.textmatching.{Literal, TextMatcher}
import com.madgag.git.bfg.textmatching.RegexReplacer._

object TextReplacementConfig {
  val lineRegex = "(.+?)(?:==>(.*))?".r

  def apply(configLines: Traversable[String]): Option[String=>String] =
    configLines.map(apply).reduceLeftOption((f, g) => Function.chain(Seq(f, g)))

  def apply(configLine: String): (String=>String) = {
    val (matcherText, replacementText) = configLine match {
      case lineRegex(matcherText, null) => (matcherText, "***REMOVED***")
      case lineRegex(matcherText, replacementText) => (matcherText, replacementText)
    }

    val textMatcher = TextMatcher(matcherText, defaultType = Literal)

    textMatcher.r --> textMatcher.typ.implicitReplacementTextEscaping(replacementText)
  }

}
