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

import com.google.common.cache.{CacheStats, CacheLoader, LoadingCache, CacheBuilder}
import com.madgag.git.bfg.cleaner._
import collection.convert.decorateAsScala._
import scala.concurrent.{ExecutionContext, Future}
import Future.successful
import ExecutionContext.Implicits.global

trait Memo[K, V] {
  def apply(z: K => V): MemoFunc[K, V]
}

trait MemoFunc[K,V] extends (K => V) {
  def asMap(): Map[K,V]

  def stats(): CacheStats

  val fix: K => Unit
}

object MemoUtil {

  def memo[K, V](f: (K => V) => MemoFunc[K, V]): Memo[K, V] = new Memo[K, V] {
    def apply(z: K => V) = f(z)
  }

  def concurrentAsyncCleanerMemo[V] = concurrentCleanerMemo[V, Future[V]](_.foreach)(successful)

  def concurrentBlockingCleanerMemo[V] = concurrentCleanerMemo[V, V](v => _(v))(identity)

  def concurrentCleanerMemo[K, V](postCalc: V => (K => Unit) => Unit)(ident: K => V): Memo[K, V] = memo[K, V] {
    (f: K => V) =>
      lazy val permanentCache = loaderCacheFor(f) { v =>
        postCalc(v)(fix) // also fix 'k' for mem-efficiency? KeptPromise lighter than DefaultPromise?
      }

      def fix(k: K) {
        // enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
        permanentCache.put(k, ident(k))
      }

      memoFunc(permanentCache, fix)
  }

  def memoFunc[K, V](loadingCache: LoadingCache[K, V], fixer: K => Unit) = new MemoFunc[K, V] {
    def apply(k: K) = loadingCache.get(k)

    def asMap() = loadingCache.asMap().asScala.view.filter {
      case (oldId, newId) => newId != oldId
    }.toMap

    override def stats(): CacheStats = loadingCache.stats()

    val fix = fixer
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
