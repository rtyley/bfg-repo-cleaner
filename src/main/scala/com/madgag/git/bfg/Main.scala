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

import cleaner._
import model.{TreeBlobEntry, FileName}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.{WindowCacheConfig, WindowCache, FileRepository}
import java.io.File
import GitUtil._
import textmatching.RegexReplacer._
import util.matching.Regex
import com.madgag.globs.openjdk.Globs
import scopt.immutable.OptionParser
import scala.Some
import io.Source
import collection.immutable.SortedSet

case class CMDConfig(stripBiggestBlobs: Option[Int] = None,
                     stripBlobsBiggerThan: Option[Int] = None,
                     protectBlobsFromRevisions: Set[String] = Set("HEAD"),
                     filterFiles: FileName => Boolean = _ => true,
                     replaceBannedStrings: Traversable[String] = List.empty,
                     replaceBannedRegex: Traversable[Regex] = List.empty,
                     gitdir: Option[File] = None)

object Main extends App {

  val wcConfig: WindowCacheConfig = new WindowCacheConfig()
  wcConfig.setStreamFileThreshold(1024 * 1024)
  WindowCache.reconfigure(wcConfig)

  val parser = new OptionParser[CMDConfig]("bfg") {
    def options = Seq(
      opt("b", "strip-blobs-bigger-than", "<size>", "strip blobs bigger than X") {
        (v: String, c: CMDConfig) => c.copy(stripBlobsBiggerThan = Some(byteSizeFrom(v)))
      },
      intOpt("B", "strip-biggest-blobs", "NUM", "strip the top NUM biggest blobs") {
        (v: Int, c: CMDConfig) => c.copy(stripBiggestBlobs = Some(v))
      },
      opt("p", "protect-blobs-from", "<refs>", "protect blobs that appear in the most recent versions of the specified refs") {
        (v: String, c: CMDConfig) => c.copy(protectBlobsFromRevisions = v.split(',').toSet)
      },
      opt("f", "filter-contents-for", "<glob>", "filter only files with the specified names") {
        (v: String, c: CMDConfig) =>
        val GlobPattern = Globs.toUnixRegexPattern(v).r

        c.copy(filterFiles = (fn => fn.string match { case GlobPattern() => true ; case _ => false }))
      },
      opt("rs", "replace-banned-strings", "<banned-strings-file>", "replace strings specified in file, one string per line") {
        (v: String, c: CMDConfig) => c.copy(replaceBannedStrings = Source.fromFile(v).getLines().toSeq)
      },
      opt("rr", "replace-banned-regex", "<banned-regex-file>", "replace regex specified in file, one regex per line") {
        (v: String, c: CMDConfig) => c.copy(replaceBannedRegex = Source.fromFile(v).getLines().map(_.r).toSeq)
      },
      arg("<repo>", "repo to clean") {
        (v: String, c: CMDConfig) =>
          val dir = new File(v).getCanonicalFile
          val gitdir = resolveGitDirFor(dir)
          if (gitdir == null || !gitdir.exists)
            throw new IllegalArgumentException("'%s' is not a valid Git repository.".format(dir.getAbsolutePath))
          c.copy(gitdir = Some(gitdir))
      }
    )

    def byteSizeFrom(v: String): Int = {
      val magnitudeChars = List('B', 'K', 'M', 'G')
      magnitudeChars.indexOf(v.takeRight(1)(0).toUpper) match {
        case -1 => throw new IllegalArgumentException("Size unit is missing (ie %s)".format(magnitudeChars.mkString(", ")))
        case index => v.dropRight(1).toInt << (index * 10)
      }
    }
  }

  parser.parse(args, CMDConfig()) map {
    config =>
      println(config)

      implicit val repo = new FileRepository(config.gitdir.get)
      implicit val progressMonitor = new TextProgressMonitor()

      println("Using repo : " + repo.getDirectory.getAbsolutePath)
      val protectedBlobIds = allBlobsReachableFrom(config.protectBlobsFromRevisions)
      println("Found " + protectedBlobIds.size + " blobs to protect")

      val blobRemoverOption = {

          val sizeBasedBlobTargetSources = Seq(
            config.stripBlobsBiggerThan.map(threshold => (s: Stream[SizedObject]) => s.takeWhile(_.size > threshold)),
            config.stripBiggestBlobs.map(num => (s: Stream[SizedObject]) => s.take(num))
          ).flatten

          sizeBasedBlobTargetSources match {
            case sources if sources.size > 0 =>
              Timing.measureTask("Finding target blobs", ProgressMonitor.UNKNOWN) {
                val biggestUnprotectedBlobs = biggestBlobs(repo).filterNot(o => protectedBlobIds(o.objectId))
                val sizedBadIds = SortedSet(sources.flatMap(_(biggestUnprotectedBlobs)): _*)
                println("Found " + sizedBadIds.size + " blob ids to remove biggest=" + sizedBadIds.max.size + " smallest=" + sizedBadIds.min.size)
                println("Total size (unpacked)=" + sizedBadIds.map(_.size).sum)
                Some(new BlobReplacer(sizedBadIds.map(_.objectId)))
              }
            case _ => None
          }
        }

      val blobTextModifierOption: Option[BlobTextModifier] = {
        val replacerOption: Option[String => String] = config.replaceBannedRegex.
          map(regex => regex --> (_ => "***REMOVED***")).reduceOption((f,g) => Function.chain(Seq(f,g)))

        // val regexReplacer = """package""".r --> (_ => "roberto")

        replacerOption.map(replacer => new BlobTextModifier {
          def lineCleanerFor(entry: TreeBlobEntry) =
            if (config.filterFiles(entry.filename)) Some(replacer) else None
        })
      }

      RepoRewriter.rewrite(repo, TreeBlobsCleaner.chain(Seq(blobRemoverOption, blobTextModifierOption).flatten))

  }

}