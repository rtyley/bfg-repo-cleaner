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

package com.madgag.git.bfg.memo

import com.google.common.cache.{CacheStats, CacheLoader, LoadingCache, CacheBuilder}
import com.madgag.git.bfg.cleaner._
import collection.convert.decorateAsScala._
import scalax.file.Path

trait Memo[K, V] {
  def apply(z: K => V): MemoFunc[K, V]
}

trait MemoFunc[K,V] extends (K => V) {
  def asMap(): Map[K,V]

  def stats(): CacheStats
}

object LoadHooks {
  def aggregate[K,V](l: Seq[LoadHooks[K,V]]): LoadHooks[K,V] = l match {
    case Seq() => new LoadHooks[K, V] {}
    case Seq(one) => one
    case multiple => new LoadHooks[K,V] {
      override def start(k: K) { multiple.foreach(_.start(k)) }

      override def end(k: K, v: V) { multiple.foreach(_.end(k, v)) }
    }
  }
}

trait LoadHooks[K,V] {
  def start(k: K) {}

  def end(k: K, v: V) {}
}

object MemoUtil {

  def memo[K, V](f: (K => V) => MemoFunc[K, V]): Memo[K, V] = new Memo[K, V] {
    def apply(z: K => V) = f(z)
  }

  /**
   *
   * A caching wrapper for a function (V => V), backed by a no-eviction LoadingCache from Google Collections.
   */
  def concurrentCleanerMemo[V](fixedEntries: Set[V] = Set.empty, additionalLoadHooks: Option[LoadHooks[V,V]] = None): Memo[V, V] = {
    memo[V, V] {
      (f: Cleaner[V]) =>
        lazy val defaultLoadHooks = new LoadHooks[V,V] {
          override def end(k: V, v: V) { fix(v) }
        }

        lazy val permanentCache = loaderCacheFor(f, LoadHooks.aggregate(additionalLoadHooks.toSeq :+ defaultLoadHooks) )

        def fix(v: V) {
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

  def loaderCacheFor[K, V](calc: K => V, loadHooks: LoadHooks[K,V]): LoadingCache[K, V] =
    CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[K, V]].recordStats().build(new CacheLoader[K, V] {
      def load(key: K): V = {
        loadHooks.start(key)
        val v = calc(key)
        loadHooks.end(key, v)
        v
      }
    })
}
