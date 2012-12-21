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


object MemoUtil {

  import com.google.common.base.Function
  import com.google.common.collect.MapMaker
  import scalaz._
  import Scalaz._

  /**
   * Modified from https://gist.github.com/458558
   *
   * A caching wrapper for a function (K => V), backed by a ConcurrentHashMap from Google Collections.
   */
  def concurrentHashMapMemo[V]: Memo[V, V] = {
    memo[V, V] {
      (f: (V => V)) =>
        val map: java.util.concurrent.ConcurrentMap[V, V] = new MapMaker().makeComputingMap(f)
        (k: V) =>
          val v = map.get(k)
          map.put(v, v) // enforce that once any value is returned, it is 'good' and therefore an identity-mapped key as well
          v
    }
  }

  implicit def ScalaFunctionToGoogleFuntion[T, R](f: T => R): Function[T, R] = new Function[T, R] {
    def apply(p1: T) = f(p1)
  }
}

