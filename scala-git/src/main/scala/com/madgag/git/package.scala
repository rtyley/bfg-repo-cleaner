package com.madgag

import org.eclipse.jgit.storage.file.{ObjectDirectory, FileRepository}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, TreeFilter}
import java.io.File
import org.eclipse.jgit.util.FS
import collection.mutable
import org.eclipse.jgit.lib.ObjectReader._
import scala.Some
import collection.convert.wrapAsScala._
import Constants._
import language.implicitConversions


package object git {
  implicit def fileRepository2ObjectDirectory(repo: FileRepository): ObjectDirectory = repo.getObjectDatabase

  def abbrId(str: String)(implicit reader: ObjectReader): ObjectId = reader.resolveExistingUniqueId(AbbreviatedObjectId.fromString(str)).get

  def singleThreadedReaderTuple(repo: Repository) = {
    val revWalk=new RevWalk(repo)
    (revWalk, revWalk.getObjectReader)
  }

  class ThreadLocalRepoResources(val repo: Repository) {
    private lazy val _objectReader = new ThreadLocal[ObjectReader] {
      override def initialValue() = repo.newObjectReader()
    }

    private lazy val _objectInserter = new ThreadLocal[ObjectInserter] {
      override def initialValue() = repo.newObjectInserter()
    }

    def objectReader() = _objectReader.get

    def objectInserter() = _objectInserter.get
  }

  implicit class RichRepo(repo: Repository) {
    lazy val git = new Git(repo)

    lazy val threadLocalRepoResources = new ThreadLocalRepoResources(repo)

    def singleThreadedReaderTuple = {
      val revWalk=new RevWalk(repo)
      (revWalk, revWalk.getObjectReader)
    }
  }

  implicit class RichString(str: String) {
    def asObjectId = org.eclipse.jgit.lib.ObjectId.fromString(str)
  }

  implicit class RichRevTree(revTree: RevTree) {
    def walk(postOrderTraversal: Boolean = false)(implicit reader: ObjectReader) = {
      val treeWalk = new TreeWalk(reader)
      treeWalk.setRecursive(true)
      treeWalk.setPostOrderTraversal(postOrderTraversal)
      treeWalk.addTree(revTree)
      treeWalk
    }
  }

  implicit def treeWalkPredicateToTreeFilter(p: TreeWalk => Boolean): TreeFilter = new TreeFilter() {
    def include(walker: TreeWalk) = p(walker)

    def shouldBeRecursive() = true

    override def clone() = this
  }

  implicit class RichTreeWalk(treeWalk: TreeWalk) {

    /**
     * @param f - note that the function must completely extract all information from the TreeWalk at the
     *          point of execution, the state of TreeWalk will be altered after execution.
     */
    def map[V](f: TreeWalk => V): Iterator[V] = new Iterator[V] {
      var _hasNext = treeWalk.next()

      def hasNext = _hasNext

      def next() = {
        val v = f(treeWalk)
        _hasNext = treeWalk.next()
        v
      }
    }
    // def flatMap[B](f: TreeWalk => Iterator[B]): C[B]

    def withFilter(p: TreeWalk => Boolean): TreeWalk = {
      treeWalk.setFilter(AndTreeFilter.create(treeWalk.getFilter, p))
      treeWalk
    }


    def foreach[U](f: TreeWalk => U) {
      while (treeWalk.next()) {
        f(treeWalk)
      }
    }

    def exists(p: TreeWalk => Boolean): Boolean = {
      var res = false
      while (!res && treeWalk.next()) res = p(treeWalk)
      res
    }
  }

  implicit class RichRef(ref: Ref) {
    def targetObjectId(implicit refDatabase: RefDatabase): ObjectId = {
      val peeledRef = refDatabase.peel(ref)
      Option(peeledRef.getPeeledObjectId).getOrElse(peeledRef.getObjectId)
    }
  }

  implicit class RichRevObject(revObject: RevObject) {
    lazy val typeString = Constants.typeString(revObject.getType)
  }

  implicit class RichObjectId(objectId: AnyObjectId) {
    def open(implicit objectReader: ObjectReader): ObjectLoader = objectReader.open(objectId)

    def asRevObject(implicit revWalk: RevWalk) = revWalk.parseAny(objectId)

    def asRevCommit(implicit revWalk: RevWalk) = revWalk.parseCommit(objectId)

    def asRevTag(implicit revWalk: RevWalk) = revWalk.parseTag(objectId)

    def asRevTree(implicit revWalk: RevWalk) = revWalk.parseTree(objectId)

    lazy val shortName = objectId.getName.take(8)
  }

  implicit class RichObjectReader(reader: ObjectReader) {
    def resolveUniquely(id: AbbreviatedObjectId): Option[ObjectId] = Some(id).map(reader.resolve).filter(_.size == 1).map(_.head)

    def resolveExistingUniqueId(id: AbbreviatedObjectId) = resolveUniquely(id).filter(reader.has)
  }

  def resolveGitDirFor(folder: File) = Option(RepositoryCache.FileKey.resolve(folder, FS.detect)).filter(_.exists())

  def treeOrBlobPointedToBy(revObject: RevObject)(implicit revWalk: RevWalk): Either[RevBlob, RevTree] = revObject match {
    case commit: RevCommit => Right(commit.getTree)
    case tree: RevTree => Right(tree)
    case blob: RevBlob => Left(blob)
    case tag: RevTag => treeOrBlobPointedToBy(tag.getObject)
  }

  def allBlobsUnder(tree: RevTree)(implicit reader: ObjectReader): Set[ObjectId] = {
    val protectedIds = mutable.Set[ObjectId]()

    for (tw <- tree.walk()) { protectedIds += tw.getObjectId(0) }

    protectedIds.toSet
  }

  // use ObjectWalk instead ??
  def allBlobsReachableFrom(revisions: Set[String])(implicit repo: Repository): Set[ObjectId] = {
    implicit val (revWalk, reader) = repo.singleThreadedReaderTuple

    revisions.map(repo.resolve).toSet.map {
      objectId: ObjectId => allBlobsReachableFrom(objectId.asRevObject)
    } reduce (_ ++ _)
  }

  def allBlobsReachableFrom(revObject: RevObject)(implicit reader: ObjectReader): Set[ObjectId] = revObject match {
    case commit: RevCommit => allBlobsUnder(commit.getTree)
    case tree: RevTree => allBlobsUnder(tree)
    case blob: RevBlob => Set(blob)
    case tag: RevTag => allBlobsReachableFrom(tag.getObject)
  }

  case class SizedObject(objectId: ObjectId, size: Long) extends Ordered[SizedObject] {
    def compare(that: SizedObject) = size.compareTo(that.size)
  }

  def biggestBlobs(implicit objectDB: ObjectDirectory, progressMonitor: ProgressMonitor = NullProgressMonitor.INSTANCE): Stream[SizedObject] = {
    val reader = objectDB.newReader
    objectDB.getPacks.flatMap {
      pack =>
        pack.map(_.toObjectId).map {
          objectId =>
            progressMonitor update 1
            SizedObject(objectId, reader.getObjectSize(objectId, OBJ_ANY))
        }
    }.toSeq.sorted.reverse.toStream.filter(oid => reader.open(oid.objectId).getType == OBJ_BLOB)
  }

}
