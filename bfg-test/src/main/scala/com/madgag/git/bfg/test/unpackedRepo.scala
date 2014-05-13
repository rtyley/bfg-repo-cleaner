package com.madgag.git.bfg.test

import scala.collection.convert.wrapAsScala._
import org.eclipse.jgit.lib.{Constants, ObjectReader, ObjectId, Repository}
import org.eclipse.jgit.revwalk.{RevObject, RevTree, RevCommit}
import org.specs2.matcher.{Matcher, MustThrownMatchers}
import com.madgag.git._
import com.madgag.git.test._
import org.specs2.specification.Scope
import org.eclipse.jgit.internal.storage.file.ObjectDirectory
import org.eclipse.jgit.treewalk.TreeWalk
// import com.madgag.git.bfg.cli.Main
// import com.madgag.git.bfg.GitUtil._

class unpackedRepo(filePath: String) extends Scope with MustThrownMatchers {

  implicit val repo = unpackRepo(filePath)
  implicit val objectDirectory = repo.getObjectDatabase.asInstanceOf[ObjectDirectory]
  implicit lazy val (revWalk, reader) = repo.singleThreadedReaderTuple


  def blobOfSize(sizeInBytes: Int): Matcher[ObjectId] = (objectId: ObjectId) => {
    val objectLoader = objectId.open
    objectLoader.getType == Constants.OBJ_BLOB && objectLoader.getSize == sizeInBytes
  }

  def packedBlobsOfSize(sizeInBytes: Long): Set[ObjectId] = {
    implicit val reader = repo.newObjectReader()
    objectDirectory.packedObjects.filter { objectId =>
      val objectLoader = objectId.open
      objectLoader.getType == Constants.OBJ_BLOB && objectLoader.getSize == sizeInBytes
    }.toSet
  }

  def haveFile(name: String) = haveTreeEntry(name, !_.isSubtree)

  def haveFolder(name: String) = haveTreeEntry(name, _.isSubtree)

  def haveTreeEntry(name: String, p: TreeWalk => Boolean) = be_==(name).atLeastOnce ^^ { (treeish: ObjectId) =>
    treeOrBlobPointedToBy(treeish.asRevObject) match {
      case Right(tree) => treeEntryNames(tree, p)
      case Left(blob) => Seq.empty[String]
    }
  }

  def treeEntryNames(t: RevTree, p: TreeWalk => Boolean): Seq[String] =
    t.walk(postOrderTraversal = true).withFilter(p).map(_.getNameString).toList

  def commitHist(specificRefs: String*)(implicit repo: Repository) = {
    val logCommand = repo.git.log
    if (specificRefs.isEmpty) logCommand.all else specificRefs.foldLeft(logCommand)((lc, ref) => lc.add(repo.resolve(ref)))
  }.call.toSeq.reverse

  def haveCommitWhereObjectIds(boom: Matcher[Traversable[ObjectId]])(implicit reader: ObjectReader): Matcher[RevCommit] = boom ^^ {
    (c: RevCommit) => c.getTree.walk().map(_.getObjectId(0)).toSeq
  }

  def haveRef(refName: String, objectIdMatcher: Matcher[ObjectId]): Matcher[Repository] = objectIdMatcher ^^ {
    (r: Repository) => r resolve (refName) aka s"Ref [$refName]"
  }

  def commitHistory(histMatcher: Matcher[Seq[RevCommit]]) = histMatcher ^^ {
    r: Repository => commitHist()(r)
  }

  def commitHistoryFor(refs: String*)(histMatcher: Matcher[Seq[RevCommit]]) = histMatcher ^^ {
    r: Repository => commitHist(refs:_*)(r)
  }

  def ensureRemovalOfBadEggs[S,T](expr : => Traversable[S], exprResultMatcher: Matcher[Traversable[S]])(block: => T) = {
    repo.git.gc.call()
    expr must exprResultMatcher

    block

    repo.git.gc.call()
    expr must beEmpty
  }

  def ensureRemovalOf[T](dirtMatchers: Matcher[Repository]*)(block: => T) = {
    // repo.git.gc.call() ??
    repo must (dirtMatchers.reduce(_ and _))
    block
    // repo.git.gc.call() ??
    repo must dirtMatchers.map(not(_)).reduce(_ and _)
  }

  def ensureInvariant[T, S](f: => S)(block: => T) = {
    val originalValue = f
    block
    f mustEqual originalValue
  }

  def ensureInvariant[T](repoMatcher: Matcher[Repository])(block: => T) = {
    repo must repoMatcher
    block
    repo must repoMatcher
  }
}
