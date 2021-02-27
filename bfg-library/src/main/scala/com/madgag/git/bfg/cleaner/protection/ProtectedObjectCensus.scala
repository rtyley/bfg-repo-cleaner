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

package com.madgag.git.bfg.cleaner.protection

import com.madgag.git._
import com.madgag.scala.collection.decorators._
import org.eclipse.jgit.lib.{ObjectId, Repository}
import org.eclipse.jgit.revwalk._

/**
 * PROTECTING TREES :
 * Want to leave the tree unchanged for all commits at the tips of refs the user thinks are important.
 * What if you think a Tag is important? Or a tree?
 *
 * If a tag points to a:
 * - commit - that commit may change, but it's tree must stay the same
 * - tree - who the fuck tags tree anyway? if I've been asked to protect it, that suggests that it's supposed to be inviolate
 * - blob - that blob will continue to be referenced by the repo, not disappear, but not be cleaned either, as we currently clean at TreeBlob level
 *
 * We can take a shortcut here by just pushing all hallowed trees straight into the memo collection
 * This does mean that we will never notice, or be able to report, if somebody sets a rule that 'cleans' (alters) a hallowed tree
 * It might also have somewhat unexpected consequences if someone hallows a very 'simple' directory that occurs often
 *
 *
 * PROTECTING BLOBS :
 * If a user wants to protect the tip of a ref, all blobs will be retained. There is no space-saving or secrets-kept
 * by deleting, tampering with those blobs elsewhere. And if you have some big-old blob like a jar that you have
 * used consistently throughout the history of your project, it benefits no-one to remove it- in fact it's actively
 * harmful.
 *
 * We explicitly protect blobs (rather than just allowing them to fall under the protection given to Trees) precisely
 * because these blobs may historically have existed in other directories (trees) that did not appear in the
 * protected tips, and so would not be protected by Tree protection.
 *
 *
 * PROTECTING TAGS & COMMITS :
 * This just means protecting the Trees & Blobs under those Tags and Commits, as specified above. Changing other
 * state - such as the message, or author, or referenced commit Ids (and consequently the object Id of the target
 * object itself) is very much up for grabs. I gotta change your history, or I've no business being here.
 */
object ProtectedObjectCensus {

  val None = ProtectedObjectCensus()

  def apply(revisions: Set[String])(implicit repo: Repository): ProtectedObjectCensus = {

    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    val objectProtection = revisions.groupBy { revision =>
      Option(repo.resolve(revision)).getOrElse { throw new IllegalArgumentException(
          s"Couldn't find '$revision' in ${repo.topDirectory.getAbsolutePath} - are you sure that exists?"
      )}.asRevObject
    }

    // blobs come from direct blob references and tag references
    // trees come from direct tree references, commit & tag references

    val treeAndBlobProtection = objectProtection.keys.groupUp(treeOrBlobPointedToBy)(_.toSet) // use Either?

    val directBlobProtection = treeAndBlobProtection collect {
      case (Left(blob), p) => blob.getId -> p
    }
    val treeProtection = treeAndBlobProtection collect {
      case (Right(tree), p) => tree -> p
    }
    val indirectBlobProtection = treeProtection.keys.flatMap(tree => allBlobsUnder(tree).map(_ -> tree)).groupUp(_._1)(_.map(_._2).toSet)

    ProtectedObjectCensus(objectProtection, treeProtection, directBlobProtection, indirectBlobProtection)
  }
}

case class ProtectedObjectCensus(protectorRevsByObject: Map[RevObject, Set[String]] = Map.empty,
                            treeProtection: Map[RevTree, Set[RevObject]] = Map.empty,
                            directBlobProtection: Map[ObjectId, Set[RevObject]] = Map.empty,
                            indirectBlobProtection: Map[ObjectId, Set[RevTree]] = Map.empty) {

  val isEmpty = protectorRevsByObject.isEmpty

  lazy val blobIds: Set[ObjectId] = directBlobProtection.keySet ++ indirectBlobProtection.keySet

  lazy val treeIds = treeProtection.keySet

  // blobs only for completeness here
  lazy val fixedObjectIds: Set[ObjectId] = treeIds ++ blobIds
}
