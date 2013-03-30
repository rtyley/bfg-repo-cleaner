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

import org.specs2.mutable.Specification

class TextReplacementConfigSpec extends Specification {
  "text replacement config" should {
    "default to using ***REMOVED*** for the replacement text" in {
      TextReplacementConfig("1234").apply("password:1234") mustEqual("password:***REMOVED***")
    }

    "use empty string as replacement text if specified" in {
      TextReplacementConfig("1234==>").apply("password:1234") mustEqual("password:")
    }

    "use literal replacement text if specified" in {
      TextReplacementConfig("1234==>mypass").apply("password:1234") mustEqual("password:mypass")
    }

    "support sub-group references in replacement text" in {
      TextReplacementConfig("""regex:Copyright \w+ (\d{4})==>Copyright Yutan $1""").apply("Copyright Roberto 2012") mustEqual("Copyright Yutan 2012")
    }

    "treat dollars and slashes in replacement text as literal if the matcher text was literal" in {
      TextReplacementConfig("""Copyright 1999==>Copyright 2013 : Price $1""").apply("Totally Copyright 1999. Boom.") mustEqual("Totally Copyright 2013 : Price $1. Boom.")
    }

    "apply transforms in the order they occur" in {
      TextReplacementConfig(Seq("awesome","some")).get.apply("Totally awesome") mustEqual("Totally ***REMOVED***")
      TextReplacementConfig(Seq("some","awesome")).get.apply("Totally awesome") mustEqual("Totally awe***REMOVED***")
    }
  }
}
