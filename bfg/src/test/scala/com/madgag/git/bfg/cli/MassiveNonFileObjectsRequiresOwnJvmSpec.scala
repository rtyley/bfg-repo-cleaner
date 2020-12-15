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

import com.madgag.git.bfg.cli.test.unpackedRepo
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// JGit has JVM-wide configuration for cache window size: https://git.eclipse.org/r/#/q/Ibf2ef604bac08885b2b3bd85f0dc31995132b682,n,z
class MassiveNonFileObjectsRequiresOwnJvmSpec extends AnyFlatSpec with Matchers {

  // concurrent testing against scala.App is not safe https://twitter.com/rtyley/status/340376844916387840

  "Massive commit messages" should "be handled without crash (ie LargeObjectException) if the user specifies that the repo contains massive non-file objects" in
    new unpackedRepo("/sample-repos/huge10MBCommitMessage.git.zip") {
      ensureRemovalFrom(commitHist("master")).ofCommitsThat(haveFile("16-kb-zeros")) {
        run("--strip-blobs-bigger-than 1K --massive-non-file-objects-sized-up-to 20M")
      }
    }

}
