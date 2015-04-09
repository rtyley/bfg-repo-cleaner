/*
 * Copyright (c) 2015 Roberto Tyley
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

package com.madgag.git.bfg.cleaner

import java.nio.charset.Charset
import java.security.{DigestInputStream, MessageDigest}

import com.google.common.io.ByteStreams
import com.madgag.git.ThreadLocalObjectDatabaseResources
import com.madgag.git.bfg.model.{FileName, TreeBlobEntry}
import org.apache.commons.codec.binary.Hex.encodeHexString
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import org.eclipse.jgit.lib.ObjectLoader

import scala.util.Try
import scalax.file.Path
import scalax.file.Path.createTempFile
import scalax.io.Resource

trait LfsBlobConverter extends TreeBlobModifier {

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  val lfsSuitableFiles: (FileName => Boolean)

  val charset = Charset.forName("UTF-8")

  val lfsObjectsDir: Path

  override def fix(entry: TreeBlobEntry) = {
    val oid = (for {
      _ <- Some(entry.filename) filter lfsSuitableFiles
      loader = threadLocalObjectDBResources.reader().open(entry.objectId)
      (shaHex, lfsPath) <- buildLfsFileFrom(loader)
    } yield {
      val pointer =
        s"""|version https://git-lfs.github.com/spec/v1
            |oid sha256:$shaHex
            |size ${loader.getSize}
            |""".stripMargin
  
      threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, pointer.getBytes(charset))
    }).getOrElse(entry.objectId)

    (entry.mode, oid)
  }

  def buildLfsFileFrom(loader: ObjectLoader): Option[(String, Path)] = {
    val tmpFile = createTempFile()

    val digest = MessageDigest.getInstance("SHA-256")

    for {
      inStream <- Resource.fromInputStream(new DigestInputStream(loader.openStream(), digest))
      outStream <- tmpFile.outputStream()
    } ByteStreams.copy(inStream, outStream)

    val shaHex = encodeHexString(digest.digest())

    val lfsPath = lfsObjectsDir / shaHex

    val ensureLfsFile = Try(if (!lfsPath.exists) tmpFile moveTo lfsPath).recover {
      case _ => lfsPath.size.contains(loader.getSize)
    }

    Try(tmpFile.delete(force = true))

    for (_ <- ensureLfsFile.toOption) yield shaHex -> lfsPath
  }
}