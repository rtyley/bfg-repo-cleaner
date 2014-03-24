/*
 * Copyright (c) 2012 - 2014 Roberto Tyley
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

package com.madgag.git


import org.specs2.mutable._
import collection.mutable
import com.madgag.git.test._


class TreeOrBlobResolverSpec extends Specification {

  implicit val repo = unpackRepo("/sample-repos/annotatedTagExample.git.zip")

  "annotated tag" should {
    "be correctly evaluated, not null" in {
      implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

      val annotatedTag = repo.resolve("chapter1").asRevTag

      treeOrBlobPointedToBy(annotatedTag) must beRight(abbrId("4c6a"))
    }
  }
}
