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

import com.madgag.git.bfg.model.{CommitNode, Footer}
import org.eclipse.jgit.lib.PersonIdent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MessageFooterSpec extends AnyFlatSpec with Matchers {

  val person = new PersonIdent("Dave Eg", "dave@e.com")

  def commit(m: String) = CommitNode(person, person, m)

  "Message footers" should "append footer without new paragraph if footers already present" in {

    val updatedCommit = commit("Sub\n\nmessage\n\nSigned-off-by: Joe Eg <joe@e.com>") add Footer("Foo", "Bar")

    updatedCommit.message shouldBe "Sub\n\nmessage\n\nSigned-off-by: Joe Eg <joe@e.com>\nFoo: Bar"
  }

  it should "create paragraph break if no footers already present" in {

    val updatedCommit = commit("Sub\n\nmessage") add Footer("Foo", "Bar")

    updatedCommit.message shouldBe "Sub\n\nmessage\n\nFoo: Bar"
  }

  // def footersViaJGit(commit: RevCommit) = commit.getFooterLines.map(f => Footer(f.getKey, f.getValue)).toList
}