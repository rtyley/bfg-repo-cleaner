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

package com.madgag.git

import com.google.common.base.Splitter
import com.madgag.git.bfg.model.FileName
import org.apache.commons.codec.binary.Hex._
import org.eclipse.jgit.lib.ObjectLoader

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.{DigestOutputStream, MessageDigest}
import scala.jdk.CollectionConverters._
import scala.util.Using

object LFS {

  val ObjectsPath: Seq[String] = Seq("lfs" , "objects")

  val PointerCharset: Charset = UTF_8

  case class Pointer(shaHex: String, blobSize: Long) {

    lazy val text: String = s"""|version https://git-lfs.github.com/spec/v1
                                |oid sha256:$shaHex
                                |size $blobSize
                                |""".stripMargin

    lazy val bytes: Array[Byte] = text.getBytes(PointerCharset)

    lazy val path: Seq[String] = Seq(shaHex.substring(0, 2), shaHex.substring(2, 4), shaHex)
  }

  object Pointer {

    val splitter = Splitter.on('\n').omitEmptyStrings().trimResults().withKeyValueSeparator(' ')

    def parse(bytes: Array[Byte]) = {
      val text = new String(bytes, PointerCharset)
      val valuesByKey= splitter.split(text).asScala
      val size = valuesByKey("size").toLong
      val shaHex = valuesByKey("oid").stripPrefix("sha256:")
      Pointer(shaHex, size)
    }
  }

  val GitAttributesFileName = FileName(".gitattributes")

  def pointerFor(loader: ObjectLoader, tmpFile: Path) = {
    val digest = MessageDigest.getInstance("SHA-256")

    Using(Files.newOutputStream(tmpFile)) { outStream =>
      loader.copyTo(new DigestOutputStream(outStream, digest))
    }

    Pointer(encodeHexString(digest.digest()), loader.getSize)
  }
}
