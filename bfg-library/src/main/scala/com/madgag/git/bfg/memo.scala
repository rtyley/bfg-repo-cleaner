/*
 * Copyright (c) 2012 Roberto Tyley
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

package com.madgag.git.bfg

import scala.jdk.CollectionConverters._
import com.google.common.cache.{CacheBuilder, CacheLoader, CacheStats, LoadingCache}
import com.madgag.git.bfg.cleaner._

trait Memo[K, V] {
  def apply(z: K => V): MemoFunc[K, V]
}

trait MemoFunc[K,V] extends (K => V) {
  def asMap(): Map[K,V]

  def stats(): CacheStats
}

object MemoUtil {

  def memo[K, V](f: (K => V) => MemoFunc[K, V]): Memo[K, V] = new Memo[K, V] {
    def apply(z: K => V) = f(z)
  }

  /**
   *
   * A caching wrapper for a function (V => V), backed by a no-eviction LoadingCache from Google Collections.
   */
  def concurrentCleanerMemo[V](fixedEntries: Set[V] = Set.empty[V]): Memo[V, V] = {
    memo[V, V] {
      (f: Cleaner[V]) =>
        lazy val permanentCache = loaderCacheFor(f)(fix)

        def fix(v: V): Unit = {
          // enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
          permanentCache.put(v, v)
        }

        fixedEntries foreach fix

        new MemoFunc[V, V] {
          def apply(k: V) = permanentCache.get(k)

          def asMap() = permanentCache.asMap().asScala.view.filter {
            case (oldId, newId) => newId != oldId
          }.toMap

          override def stats(): CacheStats = permanentCache.stats()
        }
    }
  }

  def loaderCacheFor[K, V](calc: K => V)(postCalc: V => Unit): LoadingCache[K, V] =
    CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[K, V]].recordStats().build(new CacheLoader[K, V] {
      def load(key: K): V = {
        val v = calc(key)
        postCalc(v)
        v
      }
    })
}
