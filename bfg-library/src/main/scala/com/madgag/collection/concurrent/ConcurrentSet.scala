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

package com.madgag.collection.concurrent

import scala.collection.mutable.{AbstractSet, SetOps}
import scala.collection.{IterableFactory, IterableFactoryDefaults, mutable}

class ConcurrentSet[A]()
  extends AbstractSet[A]
    with SetOps[A, ConcurrentSet, ConcurrentSet[A]]
    with IterableFactoryDefaults[A, ConcurrentSet]
{

  val m: collection.concurrent.Map[A, Boolean] = collection.concurrent.TrieMap.empty

  override def iterableFactory: IterableFactory[ConcurrentSet] = ConcurrentSet

  override def clear(): Unit = m.clear()

  override def addOne(elem: A): ConcurrentSet.this.type = {
    m.put(elem, true)
    this
  }

  override def subtractOne(elem: A): ConcurrentSet.this.type = {
    m.remove(elem)
    this
  }

  override def contains(elem: A): Boolean = m.contains(elem)

  override def iterator: Iterator[A] = m.keysIterator

}

object ConcurrentSet extends IterableFactory[ConcurrentSet] {

  @transient
  private final val EmptySet = new ConcurrentSet()

  def empty[A]: ConcurrentSet[A] = EmptySet.asInstanceOf[ConcurrentSet[A]]

  def from[A](source: collection.IterableOnce[A]): ConcurrentSet[A] =
    source match {
      case hs: ConcurrentSet[A] => hs
      case _ if source.knownSize == 0 => empty[A]
      case _ => (newBuilder[A] ++= source).result()
    }

  /** Create a new Builder which can be reused after calling `result()` without an
   * intermediate call to `clear()` in order to build multiple related results.
   */
  def newBuilder[A]: mutable.Builder[A, ConcurrentSet[A]] = ???
}
