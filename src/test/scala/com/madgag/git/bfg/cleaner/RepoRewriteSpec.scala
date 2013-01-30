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

import org.scalatest._
import matchers.ShouldMatchers
import org.eclipse.jgit.api.Git
import scala.collection.JavaConversions._
import java.util.Properties
import org.eclipse.jgit.util.RawParseUtils
import java.io.StringReader
import org.eclipse.jgit.lib.ObjectId
import PartialFunction.condOpt
import org.apache.commons.io.FilenameUtils
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.bfg.GitUtil._
import scala.Some
import com.madgag.git.bfg.textmatching.RegexReplacer._
import com.madgag.git.bfg._
import cleaner.TreeBlobsCleaner.Kit
import cli.Main.hasBeenProcessedByBFGBefore
import java.util.regex.Pattern._
import ObjectIdSubstitutor._
import org.eclipse.jgit.revwalk.RevWalk

class RepoRewriteSpec extends FlatSpec with ShouldMatchers {

  "Git repo" should "not explode" in {
    implicit val repo = unpackRepo("/sample-repos/example.git.zip")
    implicit val reader = repo.newObjectReader

    hasBeenProcessedByBFGBefore(repo) should be (false)

    val blobsToRemove = Set(abbrId("06d740"))
    RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ObjectProtection(Set("HEAD")), OldIdsPublic, Seq(FormerCommitFooter), Seq(new BlobRemover(blobsToRemove))))

    val allCommits = new Git(repo).log.all.call.toSeq

    val unwantedBlobsByCommit = allCommits.flatMap(commit => {
      val unwantedBlobs = allBlobsReachableFrom(commit).intersect(blobsToRemove).map(_.shortName)
      if (!unwantedBlobs.isEmpty) Some(commit.shortName -> unwantedBlobs) else None
    }).toMap

    unwantedBlobsByCommit should be('empty)

    allCommits.head.getFullMessage should include(FormerCommitFooter.Key)

    hasBeenProcessedByBFGBefore(repo) should be (true)
  }

  "Repo rewriter" should "clean commit messages even on clean branches, because they may reference commits from dirty ones" in {
    implicit val repo = unpackRepo("/sample-repos/taleOfTwoBranches.git.zip")
    implicit val revWalk = new RevWalk(repo)

    repo.getRef("pure").getObjectId.asRevCommit.getFullMessage should include("6e76960ede2addbbe7e")

    RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ObjectProtection(Set.empty), OldIdsPrivate, Seq(new CommitMessageObjectIdsUpdater(OldIdsPrivate)), Seq(new TreeBlobsCleaner {
      def fixer(kit: Kit) = _.entries.filterNot(_.filename.string == "sin")
    })))

    repo.getRef("pure").getObjectId.asRevCommit.getFullMessage should not include("6e76960ede2addbbe7e")
  }

  "Git repo" should "have passwords removed" in {
    implicit val repo = unpackRepo("/sample-repos/example.git.zip")
    implicit val reader = repo.newObjectReader

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
      def unapply(boom : String) = Option(FilenameUtils.getExtension(boom))
    }

    val blobTextModifier = new BlobTextModifier {
      override def lineCleanerFor(entry: TreeBlobEntry) = condOpt(entry.filename.string) {
        case FileExt("txt") | FileExt("scala") => """(\.password=).*""".r --> (_.group(1) + "*** PASSWORD ***")
      }

      val charsetDetector = new ICU4JBlobCharsetDetector
    }
    RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ObjectProtection(Set("HEAD")), OldIdsPublic, Seq(FormerCommitFooter),  Seq(blobTextModifier)))

    val allCommits = new Git(repo).log.all.call.toSeq

    val oldCommitContainingPasswords = abbrId("37bcc89")

    val cleanedCommitWithPasswordsRemoved = allCommits.find(commitThatWasFormerly(oldCommitContainingPasswords)).get

    val originalContents = passwordFileContentsIn(oldCommitContainingPasswords)
    val cleanedContents = passwordFileContentsIn(cleanedCommitWithPasswordsRemoved)

    cleanedContents should include("science")
    cleanedContents should include("database.password=")
    originalContents should include("correcthorse")
    cleanedContents should not include ("correcthorse")

    propertiesIn(cleanedContents) should have size (propertiesIn(originalContents).size)
  }


  Seq(QuickBlobCharsetDetector, new ICU4JBlobCharsetDetector).foreach { charDetector =>
    val thing = "Text modifier with "+charDetector.getClass.getSimpleName
    thing should "handle the short UTF-8" in textReplacementOf("UTF-8","bushhidthefacts", "txt","facts","toffee")

    thing should "handle the long UTF-8" in textReplacementOf("UTF-8","big", "scala","good","blessed")

    thing should "handle ASCII in SHIFT JIS" in textReplacementOf("SHIFT-JIS","japanese", "txt","EUC","BOOM")

    thing should "handle ASCII in ISO-8859-1" in textReplacementOf("ISO-8859-1","laparabla", "txt", "palpitando","buscando")

    def textReplacementOf(parentPath: String, fileNamePrefix: String, fileNamePostfix: String, before: String, after: String) {
      implicit val repo = unpackRepo("/sample-repos/encodings.git.zip")

      val blobTextModifier = new BlobTextModifier {
        def lineCleanerFor(entry: TreeBlobEntry) = Some(quote(before).r --> (_ => after))

        val charsetDetector = charDetector
      }
      RepoRewriter.rewrite(repo, ObjectIdCleaner.Config(ObjectProtection(Set.empty), OldIdsPrivate, treeBlobsCleaners = Seq(blobTextModifier)))

      val cleanedFile  = repo.resolve(s"master:$parentPath/$fileNamePrefix-ORIGINAL.$fileNamePostfix")
      val expectedFile = repo.resolve(s"master:$parentPath/$fileNamePrefix-MODIFIED-$before-$after.$fileNamePostfix")

      expectedFile should not be null
      cleanedFile should be(expectedFile)
    }
  }


}



