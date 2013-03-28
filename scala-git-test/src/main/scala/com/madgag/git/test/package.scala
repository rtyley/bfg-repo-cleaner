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

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepository
import java.io.File
import java.io.File.separatorChar
import com.madgag.compress.CompressUtil._
import com.google.common.io.Files

package object test {
  def unpackRepo(fileName: String): Repository = {
    val resolvedGitDir = unpackRepoAndGetGitDir(fileName)
    require(resolvedGitDir.exists)
    println("resolvedGitDir=" + resolvedGitDir)
    new FileRepository(resolvedGitDir)
  }

  def unpackRepoAndGetGitDir(fileName: String) = {
    val rawZipFileInputStream = getClass.getResource(fileName).openStream()
    assert(rawZipFileInputStream != null, "Stream for " + fileName + " is null.")

    val repoParentFolder = new File(Files.createTempDir(), fileName.replace(separatorChar, '_') + "-unpacked")
    repoParentFolder.mkdir()

    unzip(rawZipFileInputStream, repoParentFolder)
    rawZipFileInputStream.close
    repoParentFolder
  }
}
