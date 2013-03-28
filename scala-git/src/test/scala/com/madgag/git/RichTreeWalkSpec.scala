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

package com.madgag.git

import org.specs2.mutable._
import collection.mutable
import com.madgag.git.test._


class RichTreeWalkSpec extends Specification {

   implicit val repo = unpackRepo("/sample-repos/example.git.zip")

   "rich tree" should {
     "implement exists" in {
       implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

       val tree = abbrId("830e").asRevTree

       tree.walk().exists(_.getNameString == "one-kb-random") must beTrue
       tree.walk().exists(_.getNameString == "chimera") must beFalse
     }
     "implement map" in {
       implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

       val tree = abbrId("830e").asRevTree

       val fileNameList = tree.walk().map(_.getNameString).toList

       fileNameList must haveSize(6)

       fileNameList.groupBy(identity).mapValues(_.size) must havePair("zero" -> 2)
     }
     "implement withFilter" in {
       implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

       val tree = abbrId("830e").asRevTree

       val filteredTreeWalk = tree.walk().withFilter(_.getNameString != "zero")

       val filenames = filteredTreeWalk.map(_.getNameString).toList

       filenames must haveSize(4)

       filenames must not contain "zero"
     }
     "implement foreach" in {
       implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

       val tree = abbrId("830e").asRevTree

       val fileNames = mutable.Buffer[String]()

       tree.walk().foreach(tw => fileNames += tw.getNameString)

       fileNames.toList must haveSize(6)
     }
     "work with for comprehensions" in {
       implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

       val tree = abbrId("830e").asRevTree

       for (t <- tree.walk()) yield t.getNameString

       for (t <- tree.walk()) { t.getNameString }

       for (t <- tree.walk() if t.getNameString == "zero") { t.getDepth }

     }
   }
 }