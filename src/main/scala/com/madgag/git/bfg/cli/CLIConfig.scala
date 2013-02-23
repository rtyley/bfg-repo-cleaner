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

import java.io.File
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner._
import com.madgag.git.bfg.cleaner.TreeBlobsCleaner.Kit
import com.madgag.git.bfg.textmatching.RegexReplacer._
import com.madgag.git.bfg.model.FileName.ImplicitConversions._
import io.Source
import com.madgag.git.bfg.Timing
import org.eclipse.jgit.lib.{ObjectChecker, TextProgressMonitor, ProgressMonitor}
import collection.immutable.SortedSet
import org.eclipse.jgit.storage.file.FileRepository
import protection.ObjectProtection
import scopt.immutable.OptionParser
import scala.Some
import com.madgag.git.bfg.GitUtil.SizedObject
import com.madgag.git.bfg.model.{FileName, TreeBlobEntry}
import com.madgag.git.bfg.textmatching.{Glob, Literal, TextMatcher}
import com.madgag.inclusion._


object CLIConfig {
  val parser = new OptionParser[CLIConfig]("bfg") {
    def options = Seq(
      opt("b", "strip-blobs-bigger-than", "<size>", "strip blobs bigger than X (eg '128K', '1M', etc)") {
        (v: String, c: CLIConfig) => c.copy(stripBlobsBiggerThan = Some(ByteSize.parse(v)))
      },
      intOpt("B", "strip-biggest-blobs", "NUM", "strip the top NUM biggest blobs") {
        (v: Int, c: CLIConfig) => c.copy(stripBiggestBlobs = Some(v))
      },
      opt("D", "delete-files", "<glob>", "delete files with the specified names (eg '*.class', '*.{txt,log}' - matches on file name, not path within repo)") {
        (v: String, c: CLIConfig) => c.copy(deleteFiles = Some(FileMatcher(v)))
      },
      opt("rt", "replace-banned-text", "<banned-text-file>", "remove banned text from files and replace it with '***REMOVED***'. Banned expressions are in the specified file, one expression per line.") {
        (v: String, c: CLIConfig) => c.copy(replaceBannedStrings = Source.fromFile(v).getLines().filterNot(_.trim.isEmpty).toSeq)
      },
      opt("fi", "filter-content-including", "<glob>", "do file-content filtering on files that match the specified expression (eg '*.{txt|properties}')") {
        (v: String, c: CLIConfig) => c.copy(filenameFilters = c.filenameFilters :+ Include(FileMatcher(v)))
      },
      opt("fe", "filter-content-excluding", "<glob>", "don't do file-content filtering on files that match the specified expression (eg '*.{xml|pdf}')") {
        (v: String, c: CLIConfig) => c.copy(filenameFilters = c.filenameFilters :+ Exclude(FileMatcher(v)))
      },
      opt("fs", "filter-content-size-threshold", "<size>", "only do file-content filtering on files smaller than <size> (default is %1$d bytes)".format(CLIConfig().filterSizeThreshold)) {
        (v: String, c: CLIConfig) => c.copy(filterSizeThreshold = ByteSize.parse(v))
      },
      opt("p", "protect-blobs-from", "<refs>", "protect blobs that appear in the most recent versions of the specified refs (default is 'HEAD')") {
        (v: String, c: CLIConfig) => c.copy(protectBlobsFromRevisions = v.split(',').toSet)
      },
      //      flag("strict-object-checking", "perform additional checks on integrity of consumed & created objects") {
      //        (c: CLIConfig) => c.copy(strictObjectChecking = true)
      //      },
      flag("slow-charset-detection", "detect file-encodings using the slower & more extensive ICU4J library") {
        (c: CLIConfig) => c.copy(blobCharsetDetector = new ICU4JBlobCharsetDetector)
      },
      flag("private", "treat this repo-rewrite as removing private data (for example: omit old commit ids from commit messages)") {
        (c: CLIConfig) => c.copy(sensitiveData = Some(true))
      },
      argOpt("<repo>", "file path for Git repository to clean") {
        (v: String, c: CLIConfig) => c.copy(repoLocation = new File(v).getCanonicalFile)
      }
    )

    object FileMatcher {
      def apply(possiblyPrefixedExpr: String): TextMatcher = {
        if (possiblyPrefixedExpr.contains('/')) {
          throw new IllegalArgumentException("*** Can only match on filename, NOT path *** - remove '/' path segments")
        }
        TextMatcher(possiblyPrefixedExpr, defaultType = Glob)
      }
    }
  }
}

case class CLIConfig(stripBiggestBlobs: Option[Int] = None,
                     stripBlobsBiggerThan: Option[Int] = None,
                     protectBlobsFromRevisions: Set[String] = Set("HEAD"),
                     deleteFiles: Option[TextMatcher] = None,
                     filenameFilters: Seq[Filter[String]] = Nil,
                     filterSizeThreshold: Int = BlobTextModifier.DefaultSizeThreshold,
                     replaceBannedStrings: Traversable[String] = List.empty,
                     blobCharsetDetector: BlobCharsetDetector = QuickBlobCharsetDetector,
                     strictObjectChecking: Boolean = false,
                     sensitiveData: Option[Boolean] = None,
                     repoLocation: File = new File(System.getProperty("user.dir"))) {

  lazy val gitdir = resolveGitDirFor(repoLocation)

  implicit lazy val repo = new FileRepository(gitdir.get)

  lazy val objectProtection = ObjectProtection(protectBlobsFromRevisions)

  lazy val objectChecker = if (strictObjectChecking) Some(new ObjectChecker()) else None

  lazy val fileDeletion = deleteFiles.map {
    textMatcher =>
      val filePattern = textMatcher.r
      new TreeBlobsCleaner {
        def fixer(kit: Kit) = _.entries.filterNot(e => filePattern.matches(e.filename))
      }
  }

  lazy val lineModifier: Option[String => String] = {
    val allRegex = replaceBannedStrings.map(TextMatcher(_, defaultType = Literal).r)
    allRegex.map(regex => regex --> (_ => "***REMOVED***")).reduceOption((f, g) => Function.chain(Seq(f, g)))
  }

  lazy val filterContentPredicate: (FileName => Boolean) = f => IncExcExpression(filenameFilters) includes (f.string)

  lazy val blobTextModifier: Option[BlobTextModifier] = lineModifier.map {
    replacer =>
      new BlobTextModifier {
        override val sizeThreshold = filterSizeThreshold

        def lineCleanerFor(entry: TreeBlobEntry) = if (filterContentPredicate(entry.filename)) Some(replacer) else None

        val charsetDetector = blobCharsetDetector
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
          val sizedBadIds = SortedSet(sources.flatMap(_(biggestBlobs(repo))): _*)
          if (sizedBadIds.isEmpty) {
            println("Warning : no large blobs matching criteria found in packfiles - does the repo need to be packed?")
            None
          } else {
            println("Found " + sizedBadIds.size + " blob ids for large blobs - biggest=" + sizedBadIds.max.size + " smallest=" + sizedBadIds.min.size)
            println("Total size (unpacked)=" + sizedBadIds.map(_.size).sum)
            Some(new BlobReplacer(sizedBadIds.map(_.objectId)))
          }
        }
      case _ => None
    }
  }

  lazy val privateDataRemoval = sensitiveData.getOrElse(Seq(fileDeletion, blobTextModifier).flatten.nonEmpty)

  lazy val objectIdSubstitutor = if (privateDataRemoval) ObjectIdSubstitutor.OldIdsPrivate else ObjectIdSubstitutor.OldIdsPublic

  lazy val formerCommitFooter = if (privateDataRemoval) None else Some(FormerCommitFooter)

  lazy val commitNodeCleaners = Seq(new CommitMessageObjectIdsUpdater(objectIdSubstitutor)) ++ formerCommitFooter

  lazy val treeBlobCleaners = Seq(blobRemover, fileDeletion, blobTextModifier).flatten

  lazy val definesNoWork = treeBlobCleaners.isEmpty

  def objectIdCleanerConfig: ObjectIdCleaner.Config =
    ObjectIdCleaner.Config(
      objectProtection,
      objectIdSubstitutor,
      commitNodeCleaners,
      treeBlobCleaners,
      objectChecker
    )

  def describe = {
    if (privateDataRemoval) {
      "is removing private data, so the '" + FormerCommitFooter.Key + "' footer will not be added to commit messages."
    } else {
      "is only removing non-private data (eg, blobs that are just big, not private) : '" + FormerCommitFooter.Key + "' footer will be added to commit messages."
    }
  }
}

object ByteSize {

  import math._

  val magnitudeChars = List('K', 'M', 'G', 'T', 'P)
  val unit = 1024

  def parse(v: String): Int = {

    magnitudeChars.indexOf(v.takeRight(1)(0).toUpper) match {
      case -1 => throw new IllegalArgumentException("Size unit is missing (ie %s)".format(magnitudeChars.mkString(", ")))
      case index => v.dropRight(1).toInt << (index * 10)
    }
  }

  def format(bytes: Long): String = {
    if (bytes < unit) {
      bytes + " B"
    } else {
      val exp = (log(bytes) / log(unit)).toInt
      val pre = "KMGTPE".charAt(exp - 1)
      "%.1f %sB".format(bytes / pow(unit, exp), pre)
    }
  }

}
