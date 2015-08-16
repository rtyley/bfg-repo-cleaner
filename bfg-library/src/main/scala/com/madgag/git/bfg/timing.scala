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

import java.lang.System._
import java.util.concurrent.TimeUnit.NANOSECONDS

import org.eclipse.jgit.lib.ProgressMonitor

object Timing {
  //  def measure[T](block: => T) = {
  //    val start = nanoTime
  //    val result = block
  //    val duration = nanoTime - start
  //    println("duration="+duration)
  //    result
  //  }

  def measureTask[T](taskName: String, workSize: Int)(block: => T)(implicit progressMonitor: ProgressMonitor) = {
    progressMonitor.beginTask(taskName, workSize)
    val start = nanoTime
    val result = block
    val duration = nanoTime - start
    progressMonitor.endTask()
    println(taskName + " completed in %,d ms.".format(NANOSECONDS.toMillis(duration)))
    result
  }
}
