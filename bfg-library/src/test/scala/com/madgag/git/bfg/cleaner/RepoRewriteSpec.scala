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

import protection.ProtectedObjectCensus
import scala.collection.convert.wrapAsScala._
import java.util.Properties
import org.eclipse.jgit.util.RawParseUtils
import java.io.StringReader
import org.eclipse.jgit.lib.ObjectId
import PartialFunction.condOpt
import org.apache.commons.io.FilenameUtils
import com.madgag.git.bfg.model.TreeBlobEntry
import scala.Some
import com.madgag.textmatching.RegexReplacer._
import com.madgag.git.bfg.GitUtil._
import java.util.regex.Pattern._
import ObjectIdSubstitutor._
import org.eclipse.jgit.revwalk.RevWalk
import org.specs2.mutable._
import com.madgag.git._
import com.madgag.git.test._
import com.madgag.textmatching.Literal

class RepoRewriteSpec extends Specification {

  "Git repo" should {
    "not explode" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val reader = repo.newObjectReader

      hasBeenProcessedByBFGBefore(repo) must beFalse

      val blobsToRemove = Set(abbrId("06d740"))
      RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ProtectedObjectCensus(Set("HEAD")), Seq(FormerCommitFooter), treeBlobsCleaners = Seq(new BlobRemover(blobsToRemove))))

      val allCommits = repo.git.log.all.call.toSeq

      val unwantedBlobsByCommit = allCommits.flatMap(commit => {
        val unwantedBlobs = allBlobsReachableFrom(commit).intersect(blobsToRemove).map(_.shortName)
        if (!unwantedBlobs.isEmpty) Some(commit.shortName -> unwantedBlobs) else None
      }).toMap

      unwantedBlobsByCommit must be empty

      allCommits.head.getFullMessage must contain(FormerCommitFooter.Key)

      hasBeenProcessedByBFGBefore(repo) should beTrue
    }
  }

  "Repo rewriter" should {
    "clean commit messages even on clean branches, because commit messages may reference commits from dirty ones" in {
      implicit val repo = unpackRepo("/sample-repos/taleOfTwoBranches.git.zip")
      implicit val revWalk = new RevWalk(repo)

      def commitMessageForRev(rev: String) = repo.resolve(rev).asRevCommit.getFullMessage

      commitMessageForRev("pure") must contain("6e76960ede2addbbe7e")

      RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ProtectedObjectCensus.none, Seq(new CommitMessageObjectIdsUpdater(OldIdsPrivate)), treeBlobsCleaners = Seq(new FileDeleter(Literal("sin")))))

      commitMessageForRev("pure") must not contain ("6e76960ede2addbbe7e")
    }

    "remove passwords" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      def propertiesIn(contents: String) = {
        val p = new Properties()
        p.load(new StringReader(contents))
        p
      }

      def passwordFileContentsIn(id: ObjectId) = {
        val cleanedPasswordFile = repo.resolve(id.name + ":folder/secret-passwords.txt")
        RawParseUtils.decode(reader.open(cleanedPasswordFile).getCachedBytes)
      }

      object FileExt {
        def unapply(fileName: String) = Option(FilenameUtils.getExtension(fileName))
      }

      val blobTextModifier = new BlobTextModifier {
        override def lineCleanerFor(entry: TreeBlobEntry) = condOpt(entry.filename.string) {
          case FileExt("txt") | FileExt("scala") => """(\.password=).*""".r --> (_.group(1) + "*** PASSWORD ***")
        }

        val threadLocalObjectDBResources = repo.getObjectDatabase.threadLocalResources
      }
      val cleanedObjectMap = RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ProtectedObjectCensus(Set("HEAD")), treeBlobsCleaners = Seq(blobTextModifier)))

      val oldCommitContainingPasswords = abbrId("37bcc89")

      val cleanedCommitWithPasswordsRemoved = cleanedObjectMap(oldCommitContainingPasswords).asRevCommit

      val originalContents = passwordFileContentsIn(oldCommitContainingPasswords)
      val cleanedContents = passwordFileContentsIn(cleanedCommitWithPasswordsRemoved)

      cleanedContents must contain("science") and contain("database.password=")
      originalContents must contain("correcthorse")
      cleanedContents must not contain ("correcthorse")

      propertiesIn(cleanedContents).toMap must have size (propertiesIn(originalContents).size)
    }
  }

  "Text modifier" should {
    def textReplacementOf(parentPath: String, fileNamePrefix: String, fileNamePostfix: String, before: String, after: String) = {
      implicit val repo = unpackRepo("/sample-repos/encodings.git.zip")

      val blobTextModifier = new BlobTextModifier {
        def lineCleanerFor(entry: TreeBlobEntry) = Some(quote(before).r --> (_ => after))

        val threadLocalObjectDBResources = repo.getObjectDatabase.threadLocalResources
      }
      RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ProtectedObjectCensus.none, treeBlobsCleaners = Seq(blobTextModifier)))

      val cleanedFile = repo.resolve(s"master:$parentPath/$fileNamePrefix-ORIGINAL.$fileNamePostfix")
      val expectedFile = repo.resolve(s"master:$parentPath/$fileNamePrefix-MODIFIED-$before-$after.$fileNamePostfix")

      expectedFile should not beNull

      cleanedFile mustEqual expectedFile
    }

    "handle the short UTF-8" in textReplacementOf("UTF-8", "bushhidthefacts", "txt", "facts", "toffee")

    "handle the long UTF-8" in textReplacementOf("UTF-8", "big", "scala", "good", "blessed")

    "handle ASCII in SHIFT JIS" in textReplacementOf("SHIFT-JIS", "japanese", "txt", "EUC", "BOOM")

    "handle ASCII in ISO-8859-1" in textReplacementOf("ISO-8859-1", "laparabla", "txt", "palpitando", "buscando")
  }
}
