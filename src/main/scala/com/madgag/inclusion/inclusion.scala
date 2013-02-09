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

package com.madgag.inclusion

import scala.Function.const

case class IncExcExpression[-A](filters: Seq[Filter[A]]) {
  lazy val searchPath = (filters.headOption.map(_.impliedPredecessor).getOrElse(Include.everything) +: filters).reverse

  def includes(a: A): Boolean = searchPath.find(_.predicate(a)).get.included
}

sealed trait Filter[-A] {
  val included: Boolean

  val predicate: A => Boolean

  val impliedPredecessor: Filter[A]

  def isDefinedAt(a: A) = predicate(a)
}


object Include {
  def everything = Include(const(true))
}

object Exclude {
  def everything = Exclude(const(true))
}

case class Include[A](predicate: A => Boolean) extends Filter[A] {
  lazy val impliedPredecessor = Exclude.everything
  val included = true
}

case class Exclude[A](predicate: A => Boolean) extends Filter[A] {
  lazy val impliedPredecessor = Include.everything
  val included = false
}