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

import java.util.Date

import com.madgag.git.bfg.cleaner.CommitNodeCleaner.Kit
import com.madgag.git.bfg.model.CommitNode
import org.eclipse.jgit.lib.PersonIdent

class CommitTimestampCurfewShifter(curfew: Date => Boolean) extends CommitNodeCleaner {
  import CommitTimestampCurfewShifter._

  override def fixer(kit: Kit): Cleaner[CommitNode] = { commit =>
    val at = commit.author.getWhen
    val (aout, ain) = (findOuterBlock(curfew, at), findInnerBlock(curfew, at))
    val at2 = rescale(at, aout, ain)

    val ct = commit.committer.getWhen
    val (cout, cin) = (findOuterBlock(curfew, ct), findInnerBlock(curfew, ct))
    val ct2 = rescale(ct, cout, cin)

    changeTimestamps(commit, at2, ct2)
  }

  private def changeTimestamps(commit: CommitNode, authorWhen: Date, committerWhen: Date): CommitNode = commit.copy(
    author = new PersonIdent(commit.author, authorWhen),
    committer = new PersonIdent(commit.committer, committerWhen)
  )
}

object CommitTimestampCurfewShifter {
  type Curfew = Date => Boolean
  type Block = (Date, Date)

  def addHours(t: Date, hours: Int) = new Date(t.getTime + hours * 1000 * 60 * 60)
  def floorToHours(t: Date): Date = new Date(t.getTime / (1000 * 60 * 60) * (1000 * 60 * 60))

  def min(t: Block): Date = if (t._1.before(t._2)) t._1 else t._2
  def max(t: Block): Date = if (t._1.after(t._2)) t._1 else t._2
  def median(t: Block): Date = new Date((t._1.getTime + t._2.getTime) / 2)

  def rescale(t: Date, from: Block, to: Block): Date = {
    val lambda = (t.getTime - from._1.getTime).toDouble / (from._2.getTime - from._1.getTime).toDouble
    return new Date((to._1.getTime + (to._2.getTime - to._1.getTime) * lambda).toLong)
  }

  def findAllowed(c: Curfew, t: Date, step: Int): Date = c(t) match {
    case true => floorToHours(t)
    case false => findAllowed(c, new Date(t.getTime + step * 60 * 60 * 1000), step)
  }

  def findDisallowed(c: Curfew, t: Date, step: Int): Date = c(t) match {
    case false => floorToHours(t)
    case true => findDisallowed(c, new Date(t.getTime + step * 60 * 60 * 1000), step)
  }

  def findBlock(c: Curfew, t: Date, steps: Int): Block = steps match {
    case 0 => c(t) match {
      case true => (addHours(findDisallowed(c, t, -1), +1), findDisallowed(c, t, +1))
      case false => (addHours(findAllowed(c, t, -1), +1), findAllowed(c, t, +1))
    }
    case s if s < 0 => findBlock(c, addHours(findBlock(c, t, 0)._1, -1), steps + 1)
    case s if s > 0 => findBlock(c, findBlock(c, t, 0)._2, steps - 1)
  }

  def findOuterBlock(c: Curfew, t: Date): Block = c(t) match {
    case true => (median(findBlock(c, t, -1)), median(findBlock(c, t, +1)))
    case false => t.before(median(findBlock(c, t, 0))) match {
      case true => (median(findBlock(c, t, -2)), median(findBlock(c, t, 0)))
      case false => (median(findBlock(c, t, 0)), median(findBlock(c, t, +2)))
    }
  }

  def findInnerBlock(c: Curfew, t: Date): Block = c(t) match {
    case true => (max(findBlock(c, t, -1)), min(findBlock(c, t, +1)))
    case false => t.before(median(findBlock(c, t, 0))) match {
      case true => (max(findBlock(c, t, -2)), min(findBlock(c, t, 0)))
      case false => (max(findBlock(c, t, 0)), min(findBlock(c, t, +2)))
    }
  }
}
