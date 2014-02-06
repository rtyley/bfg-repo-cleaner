package com.madgag

import collection.convert.wrapAsScala._
import collection.mutable
import java.io.File
import language.implicitConversions
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm
import org.eclipse.jgit.diff._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.{AndTreeFilter, TreeFilter}
import org.eclipse.jgit.util.FS
import scala.util.Success
import scala.util.Try


package object git {

  def abbrId(str: String)(implicit reader: ObjectReader): ObjectId = reader.resolveExistingUniqueId(AbbreviatedObjectId.fromString(str)).get

  def singleThreadedReaderTuple(repo: Repository) = {
    val revWalk=new RevWalk(repo)
    (revWalk, revWalk.getObjectReader)
  }

  class ThreadLocalObjectDatabaseResources(objectDatabase: ObjectDatabase) {
    private lazy val _reader = new ThreadLocal[ObjectReader] {
      override def initialValue() = objectDatabase.newReader()
    }

    private lazy val _inserter = new ThreadLocal[ObjectInserter] {
      override def initialValue() = objectDatabase.newInserter()
    }

    def reader() = _reader.get

    def inserter() = _inserter.get
  }

  implicit class RichObjectDatabase(objectDatabase: ObjectDatabase) {

    lazy val threadLocalResources = new ThreadLocalObjectDatabaseResources(objectDatabase)

  }

  implicit class RichRepo(repo: Repository) {
    lazy val git = new Git(repo)

    lazy val topDirectory = if (repo.isBare) repo.getDirectory else repo.getWorkTree

    def singleThreadedReaderTuple = {
      val revWalk=new RevWalk(repo)
      (revWalk, revWalk.getObjectReader)
    }

    def nonSymbolicRefs = repo.getAllRefs.values.filterNot(_.isSymbolic)
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

  val FileModeNames = Map(
    FileMode.EXECUTABLE_FILE -> "executable",
    FileMode.REGULAR_FILE -> "regular-file",
    FileMode.SYMLINK -> "symlink",
    FileMode.TREE -> "tree",
    FileMode.MISSING -> "missing",
    FileMode.GITLINK -> "submodule"
  )

  implicit class RichFileMode(fileMode: FileMode) {
    lazy val name = FileModeNames(fileMode)
  }

  implicit class RichDiffEntry(diffEntry: DiffEntry) {
    import DiffEntry.Side
    import Side.{OLD,NEW}

    def isDiffableType(side: Side) =
      // diffEntry.getMode(side) != FileMode.GITLINK &&
        diffEntry.getId(side) != null && diffEntry.getMode(side).getObjectType == Constants.OBJ_BLOB

    lazy val bothSidesDiffableType: Boolean = Side.values().map(isDiffableType).forall(d => d)

    def editList(implicit objectReader: ObjectReader): Option[EditList] = {
      def rawText(side: Side) = {
        objectReader.resolveExistingUniqueId(diffEntry.getId(side)).map(_.open).toOption.filterNot(_.isLarge).flatMap {
          l =>
            val bytes = l.getCachedBytes
            if (RawText.isBinary(bytes)) None else Some(new RawText(bytes))
        }
      }

      if (bothSidesDiffableType) {
        for (oldText <- rawText(OLD) ; newText <- rawText(NEW)) yield {
          val algo = DiffAlgorithm.getAlgorithm(SupportedAlgorithm.HISTOGRAM)
          val comp = RawTextComparator.DEFAULT
          algo.diff(comp, oldText, newText)
        }
      } else None
    }
  }

  implicit class RichObjectId(objectId: AnyObjectId) {
    def open(implicit objectReader: ObjectReader): ObjectLoader = objectReader.open(objectId)

    def sizeOpt(implicit objectReader: ObjectReader): Option[Long] =
      if (objectReader.has(objectId)) Some(objectId.open.getSize) else None

    def asRevObject(implicit revWalk: RevWalk) = revWalk.parseAny(objectId)

    def asRevCommit(implicit revWalk: RevWalk) = revWalk.parseCommit(objectId)

    def asRevTag(implicit revWalk: RevWalk) = revWalk.parseTag(objectId)

    def asRevTree(implicit revWalk: RevWalk) = revWalk.parseTree(objectId)

    lazy val shortName = objectId.getName.take(8)
  }

  implicit class RichObjectReader(reader: ObjectReader) {
    def resolveUniquely(id: AbbreviatedObjectId): Try[ObjectId] = Try(reader.resolve(id).toList).flatMap {
      _ match {
        case fullId :: Nil => Success(fullId)
        case ids => val resolution = if (ids.isEmpty) "no Git object" else s"${ids.size} objects : ${ids.map(reader.abbreviate).map(_.name).mkString(",")}"
          throw new IllegalArgumentException(s"Abbreviated id '${id.name}' resolves to $resolution")
      }
    }

    def resolveExistingUniqueId(id: AbbreviatedObjectId) = resolveUniquely(id).flatMap {
      fullId => if (reader.has(fullId)) Success(fullId) else throw new IllegalArgumentException(s"Id '$id' not found in repo")
    }
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

}
