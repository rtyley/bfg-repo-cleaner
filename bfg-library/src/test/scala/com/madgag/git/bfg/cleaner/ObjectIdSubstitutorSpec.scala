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

package com.madgag.git.bfg.cleaner

import org.eclipse.jgit.lib.ObjectId
import org.specs2.mutable._
import ObjectIdSubstitutor.hexRegex
import com.madgag.git._
import com.madgag.git.test._
import scala.concurrent.Future
import Future.successful
import org.specs2.matcher.FutureMatchers

class ObjectIdSubstitutorSpec extends Specification with FutureMatchers {

  "Object Id Substitutor regex" should {
    "match hex strings" in {

      "01234567890" must be =~ hexRegex

      "decade2001" must be =~ hexRegex

      "This is decade2001" must be =~ hexRegex

      "This is decade2001 I say" must be =~ hexRegex

      "This is Gdecade2001 I say" must not be =~(hexRegex)

      "This is decade2001X I say" must not be =~(hexRegex)
    }
  }

  "Object Id" should {
    "be substituted in commit message" in {
      implicit val repo = unpackRepo("/sample-repos/example.git.zip")
      implicit val reader = repo.newObjectReader

      val cleanedMessage = ObjectIdSubstitutor.OldIdsPublic.replaceOldIds("See 3699910d2baab1 for backstory", reader, (_: ObjectId) => successful(abbrId("06d7405020018d")))

      cleanedMessage must be_==("See 06d7405020018d [formerly 3699910d2baab1] for backstory").await
    }
  }

}