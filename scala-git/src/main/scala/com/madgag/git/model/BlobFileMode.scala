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

package com.madgag.git.bfg.model

import org.eclipse.jgit.lib.FileMode

object BlobFileMode {
  def apply(fileMode: FileMode): Option[BlobFileMode] = fileMode match {
    case FileMode.EXECUTABLE_FILE => Some(ExecutableFile)
    case FileMode.REGULAR_FILE => Some(RegularFile)
    case FileMode.SYMLINK => Some(Symlink)
    case _ => None
  }
}

sealed abstract class BlobFileMode(val mode: FileMode)

case object ExecutableFile extends BlobFileMode(FileMode.EXECUTABLE_FILE)

case object RegularFile extends BlobFileMode(FileMode.REGULAR_FILE)

case object Symlink extends BlobFileMode(FileMode.SYMLINK)
