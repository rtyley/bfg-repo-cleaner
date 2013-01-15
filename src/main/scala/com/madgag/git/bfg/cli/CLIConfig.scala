/*
 * Copyright (c) 2012, 2013 Roberto Tyley
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

package com.madgag.git.bfg.cli

import util.matching.Regex
import java.io.File
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner.{ObjectProtection, BlobTextModifier, BlobReplacer, TreeBlobsCleaner}
import com.madgag.globs.openjdk.Globs
import com.madgag.git.bfg.cleaner.TreeBlobsCleaner.Kit
import com.madgag.git.bfg.textmatching.RegexReplacer._
import com.madgag.git.bfg.model.FileName.ImplicitConversions._
import scopt.immutable.OptionParser
import io.Source
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.bfg.Timing
import org.eclipse.jgit.lib.{ObjectChecker, TextProgressMonitor, ProgressMonitor}
import collection.immutable.SortedSet
import org.eclipse.jgit.storage.file.FileRepository

object CLIConfig {
  val parser = new OptionParser[CLIConfig]("bfg") {
    def options = Seq(
      opt("b", "strip-blobs-bigger-than", "<size>", "strip blobs bigger than X (eg '128K', '1M', etc)") {
        (v: String, c: CLIConfig) => c.copy(stripBlobsBiggerThan = Some(ByteSize.parse(v)))
      },
      intOpt("B", "strip-biggest-blobs", "NUM", "strip the top NUM biggest blobs") {
        (v: Int, c: CLIConfig) => c.copy(stripBiggestBlobs = Some(v))
      },
      opt("p", "protect-blobs-from", "<refs>", "protect blobs that appear in the most recent versions of the specified refs") {
        (v: String, c: CLIConfig) => c.copy(protectBlobsFromRevisions = v.split(',').toSet)
      },
      opt("D", "delete-files", "<glob>", "delete files with the specified names (eg '*.class', '*.{txt,log}' - matches on file name, not path)") {
        (v: String, c: CLIConfig) => c.copy(deleteFiles = Some(v))
      },
      opt("f", "filter-contents-of", "<glob>", "filter only files with the specified names (eg '*.txt', '*.{properties}')") {
        (v: String, c: CLIConfig) => c.copy(filterFiles = v)
      },
      opt("rs", "replace-banned-strings", "<banned-strings-file>", "replace strings specified in file, one string per line") {
        (v: String, c: CLIConfig) => c.copy(replaceBannedStrings = Source.fromFile(v).getLines().toSeq)
      },
      opt("rr", "replace-banned-regex", "<banned-regex-file>", "replace regex specified in file, one regex per line") {
        (v: String, c: CLIConfig) => c.copy(replaceBannedRegex = Source.fromFile(v).getLines().map(_.r).toSeq)
      },
      flag("strict-object-checking", "perform additional checks on integrity of consumed & created objects") {
        (c: CLIConfig) => c.copy(strictObjectChecking = true)
      },
      argOpt("<repo>", "repo to clean") {
        (v: String, c: CLIConfig) => c.copy(repoLocation = new File(v).getCanonicalFile)
      }
    )
  }
}

case class CLIConfig(stripBiggestBlobs: Option[Int] = None,
                     stripBlobsBiggerThan: Option[Int] = None,
                     protectBlobsFromRevisions: Set[String] = Set("HEAD"),
                     deleteFiles: Option[String] = None,
                     filterFiles: String = "*",
                     replaceBannedStrings: Traversable[String] = List.empty,
                     replaceBannedRegex: Traversable[Regex] = List.empty,
                     strictObjectChecking: Boolean = false,
                     repoLocation: File = new File(System.getProperty("user.dir"))) {

  lazy val gitdir = resolveGitDirFor(repoLocation) getOrElse (throw new IllegalArgumentException(s"'$repoLocation' is not a valid Git repository."))

  implicit lazy val repo = new FileRepository(gitdir)

  lazy val objectProtection = ObjectProtection(protectBlobsFromRevisions)

  lazy val objectChecker = if (strictObjectChecking) Some(new ObjectChecker()) else None

  lazy val fileDeletion = deleteFiles.map {
    glob =>
      val filePattern = Globs.toUnixRegexPattern(glob).r
      new TreeBlobsCleaner {
        def fixer(kit: Kit) = _.entries.filterNot(e => filePattern.matches(e.filename))
      }
  }

  lazy val lineModifier: Option[String => String] = {
    val allRegex = replaceBannedRegex ++ replaceBannedStrings.map(Regex.quoteReplacement(_).r)
    allRegex.map(regex => regex --> (_ => "***REMOVED***")).reduceOption((f, g) => Function.chain(Seq(f, g)))
  }

  lazy val blobTextModifier: Option[BlobTextModifier] = lineModifier.map {
    replacer =>
      val globPattern = Globs.toUnixRegexPattern(filterFiles).r

      new BlobTextModifier {
        def lineCleanerFor(entry: TreeBlobEntry) = if (globPattern.matches(entry.filename)) Some(replacer) else None
      }
  }

  lazy val blobRemover = {
    implicit val progressMonitor = new TextProgressMonitor()

    val sizeBasedBlobTargetSources = Seq(
      stripBlobsBiggerThan.map(threshold => (s: Stream[SizedObject]) => s.takeWhile(_.size > threshold)),
      stripBiggestBlobs.map(num => (s: Stream[SizedObject]) => s.take(num))
    ).flatten

    sizeBasedBlobTargetSources match {
      case sources if sources.size > 0 =>
        Timing.measureTask("Finding target blobs", ProgressMonitor.UNKNOWN) {
          val biggestUnprotectedBlobs = biggestBlobs(repo).filterNot(o => objectProtection.blobIds(o.objectId))
          val sizedBadIds = SortedSet(sources.flatMap(_(biggestUnprotectedBlobs)): _*)
          println("Found " + sizedBadIds.size + " blob ids to remove biggest=" + sizedBadIds.max.size + " smallest=" + sizedBadIds.min.size)
          println("Total size (unpacked)=" + sizedBadIds.map(_.size).sum)
          Some(new BlobReplacer(sizedBadIds.map(_.objectId)))
        }
      case _ => None
    }
  }

  lazy val treeBlobCleaners = TreeBlobsCleaner.chain(Seq(blobRemover, fileDeletion, blobTextModifier).flatten)

}

object ByteSize {
  val magnitudeChars = List('B', 'K', 'M', 'G')

  def parse(v: String): Int = {

    magnitudeChars.indexOf(v.takeRight(1)(0).toUpper) match {
      case -1 => throw new IllegalArgumentException("Size unit is missing (ie %s)".format(magnitudeChars.mkString(", ")))
      case index => v.dropRight(1).toInt << (index * 10)
    }
  }
}
