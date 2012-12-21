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

package com.madgag.git

import bfg.GitUtil._
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepository
import scalax.file.Path
import java.io.File._
import com.madgag.compress.CompressUtil._

package object bfg {
  def unpackRepo(fileName: String): Repository = {
    val resolvedGitDir = resolveGitDirFor(unpackRepoAndGetGitDir(fileName).jfile)
    require(resolvedGitDir.exists)
    println("resolvedGitDir=" + resolvedGitDir)
    new FileRepository(resolvedGitDir)
  }

  def unpackRepoAndGetGitDir(fileName: String) = {
    val rawZipFileInputStream = getClass.getResource(fileName).openStream()
    assert(rawZipFileInputStream != null, "Stream for " + fileName + " is null.")
    val repoParentFolder = Path.createTempDirectory(prefix = fileName.replace(separatorChar, '_') + "-unpacked")
    unzip(rawZipFileInputStream, repoParentFolder.jfile)
    rawZipFileInputStream.close
    repoParentFolder
  }
}
