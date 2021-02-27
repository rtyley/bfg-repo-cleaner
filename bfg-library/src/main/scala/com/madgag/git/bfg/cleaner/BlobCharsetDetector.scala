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

package com.madgag.git.bfg.cleaner

import com.google.common.io.ByteStreams
import com.google.common.io.ByteStreams.toByteArray
import com.madgag.git.bfg.model.TreeBlobEntry
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectLoader

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction._
import scala.util.{Try, Using}


trait BlobCharsetDetector {
  // should return None if this is a binary file that can not be converted to text
  def charsetFor(entry: TreeBlobEntry, objectLoader: ObjectLoader): Option[Charset]
}


object QuickBlobCharsetDetector extends BlobCharsetDetector {

  val CharSets: Seq[Charset] =
    Seq(Charset.forName("UTF-8"), Charset.defaultCharset(), Charset.forName("ISO-8859-1")).distinct

  def charsetFor(entry: TreeBlobEntry, objectLoader: ObjectLoader): Option[Charset] = {
    Using(ByteStreams.limit(objectLoader.openStream(), 8000))(toByteArray).toOption.filterNot(RawText.isBinary).flatMap {
      sampleBytes =>
        val b = ByteBuffer.wrap(sampleBytes)
        CharSets.find(cs => Try(decode(b, cs)).isSuccess)
    }
  }

  private def decode(b: ByteBuffer, charset: Charset): Unit = {
    charset.newDecoder.onMalformedInput(REPORT).onUnmappableCharacter(REPORT).decode(b)
  }
}



