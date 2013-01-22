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

import com.google.common.cache.{CacheLoader, LoadingCache, CacheBuilder}

object MemoUtil {

  import scalaz._
  import Scalaz._

  /**
   *
   * A caching wrapper for a function (V => V), backed by a no-eviction LoadingCache from Google Collections.
   */
  def concurrentCleanerMemo[V](fixedEntries: Set[V] = Set.empty): Memo[V, V] = {
    memo[V, V] {
      (f: (V => V)) =>
        val permanentCache = loaderCacheFor(f)

        def fix(v: V) = permanentCache.put(v, v)

        fixedEntries foreach fix

        (k: V) =>
          val v = permanentCache.get(k)
          fix(v) // enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
          v
    }
  }

  def loaderCacheFor[K,V](f: K => V): LoadingCache[K, V] = CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[K, V]]
    .build(new CacheLoader[K, V] {
      def load(key: K): V = f(key)
    })

}

