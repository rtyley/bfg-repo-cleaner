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

package com.madgag.git.bfg.cli

import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.{WindowCacheConfig, WindowCache, FileRepository}
import collection.immutable.SortedSet
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner._
import com.madgag.git.bfg.Timing
import scala.Some
import com.madgag.git.bfg.GitUtil.SizedObject
import com.madgag.git.bfg.model.TreeBlobEntry

object Main extends App {

  val wcConfig: WindowCacheConfig = new WindowCacheConfig()
  wcConfig.setStreamFileThreshold(1024 * 1024)
  WindowCache.reconfigure(wcConfig)

  CMDConfig.parser.parse(args, CMDConfig()) map {
    config =>
      println(config)

      implicit val repo = new FileRepository(config.gitdir)
      implicit val progressMonitor = new TextProgressMonitor()

      println("Using repo : " + repo.getDirectory.getAbsolutePath)
      val objectProtection = ObjectProtection(config.protectBlobsFromRevisions)
      println("Found " + objectProtection.fixedObjectIds.size + " objects to protect")

      val blobRemoverOption = {

          val sizeBasedBlobTargetSources = Seq(
            config.stripBlobsBiggerThan.map(threshold => (s: Stream[SizedObject]) => s.takeWhile(_.size > threshold)),
            config.stripBiggestBlobs.map(num => (s: Stream[SizedObject]) => s.take(num))
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

      val blobTextModifierOption: Option[BlobTextModifier] = config.lineModifierOption.map(replacer => new BlobTextModifier {
        def lineCleanerFor(entry: TreeBlobEntry) = if (config.filterFilesPredicate(entry.filename)) Some(replacer) else None
      })

      val treeBlobCleaners = TreeBlobsCleaner.chain(Seq(blobRemoverOption, config.fileDeleterOption, blobTextModifierOption).flatten)
      RepoRewriter.rewrite(repo, treeBlobCleaners, objectProtection)
  }

}