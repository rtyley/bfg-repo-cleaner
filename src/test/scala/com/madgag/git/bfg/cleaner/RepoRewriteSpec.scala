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

class RepoRewriteSpec extends FlatSpec with ShouldMatchers {

  "Git repo" should "not explode" in {
    implicit val repo = unpackRepo("/sample-repos/example.git.zip")
    implicit val reader = repo.newObjectReader

    val blobsToRemove = Set(abbrId("06d740"))
    RepoRewriter.rewrite(repo, new BlobRemover(blobsToRemove), ObjectProtection(Set("master")))

    val allCommits = new Git(repo).log.all.call.toSeq

    val unwantedBlobsByCommit = allCommits.flatMap(commit => {
      val unwantedBlobs = allBlobsReachableFrom(commit).intersect(blobsToRemove).map(_.shortName)
      if (!unwantedBlobs.isEmpty) Some(commit.shortName -> unwantedBlobs) else None
    }).toMap

    unwantedBlobsByCommit should be('empty)

    allCommits.head.getFullMessage should include(FormerCommitFooter.Key)
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

    RepoRewriter.rewrite(repo, new BlobTextModifier {
      override def lineCleanerFor(entry: TreeBlobEntry) = condOpt(entry.filename.string) {
        case FileExt("txt") | FileExt("scala") => """(\.password=).*""".r --> (_.group(1) + "*** PASSWORD ***")
      }
    }, ObjectProtection(Set("master")))

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
}



