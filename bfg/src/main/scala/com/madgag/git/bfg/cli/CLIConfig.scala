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

import com.madgag.git.bfg.BuildInfo
import com.madgag.git.bfg.GitUtil._
import com.madgag.git.bfg.cleaner._
import com.madgag.git.bfg.cleaner.kit.BlobInserter
import com.madgag.git.bfg.cleaner.protection.ProtectedObjectCensus
import com.madgag.git.bfg.model.FileName.ImplicitConversions._
import com.madgag.git.bfg.model.{FileName, Tree, TreeBlobEntry, TreeBlobs, TreeSubtrees}
import com.madgag.git.{SizedObject, _}
import com.madgag.inclusion.{IncExcExpression, _}
import com.madgag.text.ByteSize
import com.madgag.textmatching.{Glob, TextMatcher, TextMatcherType, TextReplacementConfig}
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import scopt.{OptionParser, Read}

import scalax.file.ImplicitConversions._


object CLIConfig {
  val parser = new OptionParser[CLIConfig]("bfg") {

    def fileMatcher(name: String, defaultType: TextMatcherType = Glob) = {
      implicit val textMatcherRead = Read.reads { TextMatcher(_, defaultType) }

      opt[TextMatcher](name).valueName(s"<${defaultType.expressionPrefix}>").validate { m =>
        if (m.expression.contains('/')) {
          failure("*** Can only match on filename, NOT path *** - remove '/' path segments")
        } else success
      }
    }

    val exactVersion = BuildInfo.version + (if (BuildInfo.version.contains("-SNAPSHOT")) s" (${BuildInfo.gitDescription})" else "")

    head("bfg", exactVersion)
    version("version").hidden()

    opt[String]('b', "strip-blobs-bigger-than").valueName("<size>").text("strip blobs bigger than X (eg '128K', '1M', etc)").action {
      (v , c) => c.copy(stripBlobsBiggerThan = Some(ByteSize.parse(v)))
    }
    opt[Int]('B', "strip-biggest-blobs").valueName("NUM").text("strip the top NUM biggest blobs").action {
      (v, c) => c.copy(stripBiggestBlobs = Some(v))
    }
    opt[File]("strip-blobs-with-ids").abbr("bi").valueName("<blob-ids-file>").text("strip blobs with the specified Git object ids").action {
      (v, c) => c.copy(stripBlobsWithIds = Some(v.lines().map(_.trim).filterNot(_.isEmpty).map(_.asObjectId).toSet))
    }
    fileMatcher("delete-files").abbr("D").text("delete files with the specified names (eg '*.class', '*.{txt,log}' - matches on file name, not path within repo)").action {
      (v, c) => c.copy(deleteFiles = Some(v))
    }
    fileMatcher("delete-folders").text("delete folders with the specified names (eg '.svn', '*-tmp' - matches on folder name, not path within repo)").action {
      (v, c) => c.copy(deleteFolders = Some(v))
    }
    opt[String]("convert-to-git-lfs").text("extract files with the specified names (eg '*.zip' or '*.mp4') into Git LFS").action {
      (v, c) => c.copy(lfsConversion = Some(v))
    }
    opt[File]("replace-text").abbr("rt").valueName("<expressions-file>").text("filter content of files, replacing matched text. Match expressions should be listed in the file, one expression per line - " +
      "by default, each expression is treated as a literal, but 'regex:' & 'glob:' prefixes are supported, with '==>' to specify a replacement " +
      "string other than the default of '***REMOVED***'.").action {
      (v, c) => c.copy(textReplacementExpressions = v.lines().filterNot(_.trim.isEmpty).toSeq)
    }
    fileMatcher("filter-content-including").abbr("fi").text("do file-content filtering on files that match the specified expression (eg '*.{txt,properties}')").action {
      (v, c) => c.copy(filenameFilters = c.filenameFilters :+ Include(v))
    }
    fileMatcher("filter-content-excluding").abbr("fe").text("don't do file-content filtering on files that match the specified expression (eg '*.{xml,pdf}')").action {
      (v, c) => c.copy(filenameFilters = c.filenameFilters :+ Exclude(v))
    }
    opt[String]("filter-content-size-threshold").abbr("fs").valueName("<size>").text("only do file-content filtering on files smaller than <size> (default is %1$d bytes)".format(CLIConfig().filterSizeThreshold)).action {
      (v, c) => c.copy(filterSizeThreshold = ByteSize.parse(v))
    }
    opt[String]("blob-exec").abbr("be").valueName("<cmd>").text("execute the system command for each blob").action {
      (v, c) => c.copy(blobExec = Some(v))
    }
    opt[String]('p', "protect-blobs-from").valueName("<refs>").text("protect blobs that appear in the most recent versions of the specified refs (default is 'HEAD')").action {
      (v, c) => c.copy(protectBlobsFromRevisions = v.split(',').toSet)
    }
    opt[Unit]("no-blob-protection").text("allow the BFG to modify even your *latest* commit. Not recommended: you should have already ensured your latest commit is clean.").action {
      (_, c) => c.copy(protectBlobsFromRevisions = Set.empty)
    }
    opt[Unit]("strict-object-checking").text("perform additional checks on integrity of consumed & created objects").hidden().action {
      (_, c) => c.copy(strictObjectChecking = true)
    }
    opt[Unit]("private").text("treat this repo-rewrite as removing private data (for example: omit old commit ids from commit messages)").action {
      (_, c) => c.copy(sensitiveData = Some(true))
    }
    opt[String]("massive-non-file-objects-sized-up-to").valueName("<size>").text("increase memory usage to handle over-size Commits, Tags, and Trees that are up to X in size (eg '10M')").action {
      (v, c) => c.copy(massiveNonFileObjects = Some(ByteSize.parse(v)))
    }
    opt[String]("fix-filename-duplicates-preferring").valueName("<filemode>").text("Fix corrupt trees which contain multiple entries with the same filename, favouring the 'tree' or 'blob'").hidden().action {
      (v, c) =>
        val preferredFileMode = v.toLowerCase match {
          case "tree" | "folder" => FileMode.TREE
          case "blob" | "file" => FileMode.REGULAR_FILE
          case other => throw new IllegalArgumentException(s"'$other' should be 'tree' or 'blob'")
        }
        val ord: Option[Ordering[FileMode]] = Some(Ordering.by[FileMode, Int](filemode => if (filemode==preferredFileMode) 0 else 1))

        c.copy(fixFilenameDuplicatesPreferring = ord)
    }
    arg[File]("<repo>") optional() action { (x, c) =>
      c.copy(repoLocation = x) } text("file path for Git repository to clean")
  }
}

case class CLIConfig(stripBiggestBlobs: Option[Int] = None,
                     stripBlobsBiggerThan: Option[Long] = None,
                     protectBlobsFromRevisions: Set[String] = Set("HEAD"),
                     deleteFiles: Option[TextMatcher] = None,
                     deleteFolders: Option[TextMatcher] = None,
                     fixFilenameDuplicatesPreferring: Option[Ordering[FileMode]] = None,
                     filenameFilters: Seq[Filter[String]] = Nil,
                     filterSizeThreshold: Long = BlobTextModifier.DefaultSizeThreshold,
                     textReplacementExpressions: Traversable[String] = List.empty,
                     blobExec: Option[String] = None,
                     stripBlobsWithIds: Option[Set[ObjectId]] = None,
                     lfsConversion: Option[String] = None,
                     strictObjectChecking: Boolean = false,
                     sensitiveData: Option[Boolean] = None,
                     massiveNonFileObjects: Option[Long] = None,
                     repoLocation: File = new File(System.getProperty("user.dir"))) {

  lazy val gitdir = resolveGitDirFor(repoLocation)

  implicit lazy val repo = FileRepositoryBuilder.create(gitdir.get).asInstanceOf[FileRepository]

  lazy val objectProtection = ProtectedObjectCensus(protectBlobsFromRevisions)

  lazy val objectChecker = if (strictObjectChecking) Some(new ObjectChecker()) else None

  lazy val fileDeletion: Option[Cleaner[TreeBlobs]] = deleteFiles.map {
    textMatcher => new FileDeleter(textMatcher)
  }

  lazy val folderDeletion: Option[Cleaner[TreeSubtrees]] = deleteFolders.map {
    textMatcher => { subtrees: TreeSubtrees =>
      TreeSubtrees(subtrees.entryMap.filterKeys(filename => !textMatcher(filename)))
    }
  }

  lazy val fixFileNameDuplication: Option[Cleaner[Seq[Tree.Entry]]] = fixFilenameDuplicatesPreferring.map {
    implicit preferredFileModes =>
    { treeEntries: Seq[Tree.Entry] => treeEntries.groupBy(_.name).values.map(_.minBy(_.fileMode)).toSeq }
  }

  lazy val lineModifier: Option[String => String] =
    TextReplacementConfig(textReplacementExpressions, "***REMOVED***")

  lazy val filterContentPredicate: (FileName => Boolean) = f => IncExcExpression(filenameFilters) includes (f.string)

  lazy val blobTextModifier: Option[BlobTextModifier] = lineModifier.map {
    replacer =>
      new BlobTextModifier {
        override val sizeThreshold = filterSizeThreshold

        def lineCleanerFor(entry: TreeBlobEntry) = if (filterContentPredicate(entry.filename)) Some(replacer) else None

        val threadLocalObjectDBResources = repo.getObjectDatabase.threadLocalResources
      }
  }

  lazy val lfsBlobConverter: Option[LfsBlobConverter] = lfsConversion.map { lfsGlobExpr =>
    new LfsBlobConverter(lfsGlobExpr, repo)
  }

  lazy val blobExecModifier: Option[BlobExecModifier] = blobExec.map {
    execCommand =>
      new BlobExecModifier {
        val command = execCommand

        val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources = repo.getObjectDatabase.threadLocalResources
      }
  }

  lazy val privateDataRemoval = sensitiveData.getOrElse(Seq(fileDeletion, folderDeletion, blobTextModifier).flatten.nonEmpty)

  lazy val objectIdSubstitutor = if (privateDataRemoval) ObjectIdSubstitutor.OldIdsPrivate else ObjectIdSubstitutor.OldIdsPublic

  lazy val treeEntryListCleaners = fixFileNameDuplication.toSeq

  lazy val commitNodeCleaners = {
    lazy val formerCommitFooter = if (privateDataRemoval) None else Some(FormerCommitFooter)

    Seq(new CommitMessageObjectIdsUpdater(objectIdSubstitutor)) ++ formerCommitFooter
  }

  lazy val treeBlobCleaners: Seq[Cleaner[TreeBlobs]] = {

    lazy val blobsByIdRemover: Option[BlobRemover] = stripBlobsWithIds.map(new BlobRemover(_))

    lazy val blobRemover: Option[Cleaner[TreeBlobs]] = {
      implicit val progressMonitor: ProgressMonitor = new TextProgressMonitor()

      val sizeBasedBlobTargetSources = Seq(
        stripBlobsBiggerThan.map(threshold => (s: Stream[SizedObject]) => s.takeWhile(_.size > threshold)),
        stripBiggestBlobs.map(num => (s: Stream[SizedObject]) => s.take(num))
      ).flatten

      if (sizeBasedBlobTargetSources.isEmpty) None else {
        val sizedBadIds = sizeBasedBlobTargetSources.flatMap(_(biggestBlobs(repo.getObjectDatabase, progressMonitor))).toSet
        if (sizedBadIds.isEmpty) {
          println("Warning : no large blobs matching criteria found in packfiles - does the repo need to be packed?")
          None
        } else {
          println("Found " + sizedBadIds.size + " blob ids for large blobs - biggest=" + sizedBadIds.max.size + " smallest=" + sizedBadIds.min.size)
          println("Total size (unpacked)=" + sizedBadIds.map(_.size).sum)
          Some(new BlobReplacer(sizedBadIds.map(_.objectId), new BlobInserter(repo.getObjectDatabase.threadLocalResources.inserter())))
        }
      }
    }

    Seq(blobsByIdRemover, blobRemover, fileDeletion, blobTextModifier, lfsBlobConverter, blobExecModifier).flatten
  }

  lazy val definesNoWork = treeBlobCleaners.isEmpty && folderDeletion.isEmpty && treeEntryListCleaners.isEmpty

  def objectIdCleanerConfig: ObjectIdCleaner.Config =
    ObjectIdCleaner.Config(
      objectProtection,
      objectIdSubstitutor,
      commitNodeCleaners,
      treeEntryListCleaners,
      treeBlobCleaners,
      folderDeletion.toSeq,
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
