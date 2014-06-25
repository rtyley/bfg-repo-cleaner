package com.madgag.git.bfg.cleaner

import java.text.SimpleDateFormat
import java.util.Date

import com.madgag.collection.concurrent.ConcurrentMultiMap
import com.madgag.git._
import com.madgag.git.bfg.cleaner.protection.{ProtectedObjectCensus, ProtectedObjectDirtReport}
import com.madgag.git.bfg.log.JobLogContext
import com.madgag.git.bfg.model.FileName
import com.madgag.text.Text._
import com.madgag.text.{ByteSize, Tables, Text}
import org.eclipse.jgit.diff.DiffEntry.ChangeType._
import org.eclipse.jgit.diff._
import org.eclipse.jgit.lib.FileMode._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.transport.ReceiveCommand

import scala.collection.convert.wrapAll._
import scala.collection.immutable.SortedMap
import scalax.file.Path

trait Reporter {

  def reportRefsForScan(allRefs: Traversable[Ref])(implicit objReader: ObjectReader)

  def reportRefUpdateStart(refUpdateCommands: Traversable[ReceiveCommand])

  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk)

  def reportCleaningStart(commits: Seq[RevCommit])

  def reportResults(commits: List[RevCommit], objectIdCleaner: ObjectIdCleaner)
}

object ReportsDir {
  def generateReportsDirFor(repo: Repository) = {
    val now = new Date()
    def format(s: String) = new SimpleDateFormat(s).format(now)
    val dir = Path.fromString(repo.topDirectory.getAbsolutePath + ".bfg-report") / format("yyyy-MM-dd") / format("HH-mm-ss")
    dir.doCreateDirectory()
    dir
  }
}

class CLIReporter(repo: Repository, implicit val jl: JobLogContext) extends Reporter {

  val logger = jl.logContext.getLogger("cli")

  lazy val reportsDir = jl.reportsDir

  def reportRefUpdateStart(refUpdateCommands: Traversable[ReceiveCommand]) {
    logger.info(title(s"Updating ${plural(refUpdateCommands, "Ref")}"))

    val summaryTableCells = refUpdateCommands.map(update => (update.getRefName, update.getOldId.shortName, update.getNewId.shortName))

    logger.info(Tables.tableText(("Ref", "Before", "After"), summaryTableCells.toSeq))
  }

  def reportRefsForScan(allRefs: Traversable[Ref])(implicit objReader: ObjectReader) {
    val refsByObjType = allRefs.groupBy {
      ref => objReader.open(ref.getObjectId).getType
    } withDefault Seq.empty

    refsByObjType.foreach {
      case (typ, refs) => logger.info("Found " + refs.size + " " + Constants.typeString(typ) + "-pointing refs : " + abbreviate(refs.map(_.getName).toSeq, "...", 4).mkString(", "))
    }
  }


  // abort due to Dirty Tips on Private run - user needs to manually clean
  // warn due to Dirty Tips on Public run - it's not so serious if users publicise dirty tips.
  // if no protection
  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk) {
    logger.info(title("Protected commits"))

    if (objectIdCleanerConfig.protectedObjectCensus.isEmpty) {
      logger.info("You're not protecting any commits, which means the BFG will modify the contents of even *current* commits.\n\n" +
        "This isn't recommended - ideally, if your current commits are dirty, you should fix up your working copy and " +
        "commit that, check that your build still works, and only then run the BFG to clean up your history.")
    } else {
      logger.info("These are your protected commits, and so their contents will NOT be altered:\n")

      val unprotectedConfig = objectIdCleanerConfig.copy(protectedObjectCensus = ProtectedObjectCensus.None)

      reportProtectedCommitsAndTheirDirt(objectIdCleanerConfig)
    }
  }

  case class DiffSideDetails(id: ObjectId, path: String, mode: FileMode, size: Option[Long])

  def reportProtectedCommitsAndTheirDirt(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk) {
    implicit val reader = revWalk.getObjectReader

    def diffDetails(d: DiffEntry) = {
      val side = DiffEntry.Side.OLD
      val id: ObjectId = d.getId(side).toObjectId
      DiffSideDetails(id, d.getPath(side), d.getMode(side), id.sizeOpt)
    }

    def fileInfo(d: DiffSideDetails) = {
      val extraInfo = (d.mode match {
        case GITLINK => Some("submodule")
        case _ => d.size.map(ByteSize.format(_))
      }).map(e => s"($e)")

      (d.path +: extraInfo.toSeq).mkString(" ")
    }

    val protectedDirtDir = reportsDir / "protected-dirt"
    protectedDirtDir.doCreateDirectory()

    val reports = ProtectedObjectDirtReport.reportsFor(objectIdCleanerConfig, objectDB)

    reports.foreach {
      report =>
        val protectorRevs = objectIdCleanerConfig.protectedObjectCensus.protectorRevsByObject(report.revObject)
        val objectTitle = s" * ${report.revObject.typeString} ${report.revObject.shortName} (protected by '${protectorRevs.mkString("', '")}')"

        report.dirt match {
          case None => logger.info(objectTitle)
          case Some(diffEntries) =>
            if (diffEntries.isEmpty) {
              logger.info(objectTitle + " - dirty")
            } else {
              logger.info(objectTitle + " - contains " + plural(diffEntries, "dirty file") + " : ")
              abbreviate(diffEntries.view.map(diffDetails).map(fileInfo), "...").foreach {
                dirtyFile => logger.info("\t- " + dirtyFile)
              }

              val protectorRefsFileNameSafe = protectorRevs.mkString("_").replace(protectedDirtDir.separator, "-")
              val diffFile = protectedDirtDir / s"${report.revObject.shortName}-${protectorRefsFileNameSafe}.csv"

              diffFile.writeStrings(diffEntries.map {
                diffEntry =>
                  val de = diffDetails(diffEntry)

                  val modifiedLines = if (diffEntry.getChangeType == MODIFY) diffEntry.editList.map(changedLinesFor) else None

                  val elems = Seq(de.id.name, diffEntry.getChangeType.name, de.mode.name, de.path, de.size.getOrElse(""), modifiedLines.getOrElse(""))

                  elems.mkString(",")
              }, "\n")
              }
            }
        }

    val dirtyReports = reports.filter(_.objectProtectsDirt)
    if (dirtyReports.nonEmpty) {

      logger.info(s"""
      |WARNING: The dirty content above may be removed from other commits, but as
      |the *protected* commits still use it, it will STILL exist in your repository.
      |
      |If you *really* want this content gone, make a manual commit that removes it,
      |and then run the BFG on a fresh copy of your repo.
       """.stripMargin)
      // TODO would like to abort here if we are cleaning 'private' data.
    }
  }

  def changedLinesFor(edits: EditList): String = {
    edits.map {
      edit => Seq(edit.getBeginA + 1, edit.getEndA).distinct.mkString("-")
    }.mkString(";")
  }

  def reportCleaningStart(commits: Seq[RevCommit]) {
    logger.info(s"${title("Cleaning")}\n\nFound ${commits.size} commits")
  }

  def reportResults(commits: List[RevCommit], objectIdCleaner: ObjectIdCleaner) {
    def reportTreeDirtHistory() {

      val dirtHistoryElements = math.max(20, math.min(60, commits.size))
      def cut[A](xs: Seq[A], n: Int) = {
        val avgSize = xs.size.toFloat / n
        def startOf(unit: Int): Int = math.round(unit * avgSize)
        (0 until n).view.map(u => xs.slice(startOf(u), startOf(u + 1)))
      }
      val treeDirtHistory = cut(commits, dirtHistoryElements).map {
        case commits if commits.isEmpty => ' '
        case commits if (commits.exists(c => objectIdCleaner.isDirty(c.getTree))) => 'D'
        case commits if (commits.exists(objectIdCleaner.isDirty)) => 'm'
        case _ => '.'
      }.mkString
      def leftRight(markers: Seq[String]) = markers.mkString(" " * (treeDirtHistory.length - markers.map(_.size).sum))

      val treeDirtDiagram = Seq(
        leftRight(Seq("Earliest", "Latest")),
        leftRight(Seq("|", "|")),
        treeDirtHistory,
        "",
        "D = dirty commits (file tree fixed)",
        "m = modified commits (commit message or parents changed)",
        ". = clean commits (no changes to file tree)"
      )

      logger.info(title("Commit Tree-Dirt History"))
      logger.info(treeDirtDiagram.map("\n\t"+_).mkString)

      val firstModifiedCommit = commits.find(objectIdCleaner.isDirty).map(_ -> "First modified commit")
      val lastDirtyCommit = commits.reverse.find(c => objectIdCleaner.isDirty(c.getTree)).map(_ -> "Last dirty commit")
      val items = for {
        (commit, desc) <- firstModifiedCommit ++ lastDirtyCommit
        (before, after) <- objectIdCleaner.substitution(commit)
      } yield (desc, before.shortName, after.shortName)
      logger.info(Tables.tableText(("", "Before", "After"), items.toSeq))
    }

    reportTreeDirtHistory()

    lazy val mapFile = reportsDir / "object-id-map.old-new.txt"
    lazy val cacheStatsFile = reportsDir / "cache-stats.txt"

    val changedIds = objectIdCleaner.cleanedObjectMap()

    def reportFiles[FI](
        fileData: ConcurrentMultiMap[FileName, FI],
        actionType: String,
        tableTitles: Product
      )(f: ((FileName,Set[FI])) => Product)(fi: FI => Seq[String]) {
      implicit val fileNameOrdering = Ordering[String].on[FileName](_.string)

      val dataByFilename = SortedMap[FileName, Set[FI]](fileData.toMap.toSeq: _*)
      if (dataByFilename.nonEmpty) {
        println(title(s"$actionType files"))
        Tables.formatTable(tableTitles, dataByFilename.map(f).toSeq).map("\t" + _).foreach(println)

        (reportsDir / s"${actionType.toLowerCase}-files.txt").writeStrings(dataByFilename.flatMap {
          case (filename, changes) => changes.map(fi.andThen(fid => (fid :+ filename).mkString(" ")))
        }, "\n")
      }
    }

    reportFiles(objectIdCleaner.changesByFilename, "Changed", ("Filename", "Before & After")) {
      case (filename, changes) => (filename, Text.abbreviate(changes.map {case (oldId, newId) => oldId.shortName+" ⇒ "+newId.shortName}, "...").mkString(", "))
    } { case (oldId, newId) => Seq(oldId.name, newId.name) }

    implicit val reader = objectIdCleaner.threadLocalResources.reader()

    reportFiles(objectIdCleaner.deletionsByFilename, "Deleted", ("Filename", "Git id")) {
      case (filename, oldIds) => (filename, Text.abbreviate(oldIds.map(oldId => oldId.shortName + oldId.sizeOpt.map(size => s" (${ByteSize.format(size)})").mkString), "...").mkString(", "))
    } { oldId => Seq(oldId.name, oldId.sizeOpt.mkString) }

    logger.info(s"\nIn total, ${changedIds.size} object ids were changed.")

    mapFile.writeStrings(SortedMap[AnyObjectId, ObjectId](changedIds.toSeq: _*).view.map { case (o,n) => s"${o.name} ${n.name}"}, "\n")

    cacheStatsFile.writeStrings(objectIdCleaner.stats().seq.map(_.toString()), "\n")

    logger.info(s"""
      |A report on this BFG job, including logs, protected dirt, changed
      |object-ids, etc, has been written to :
      |
      |\t${reportsDir.path}
       """.stripMargin)

    logger.info("\nBFG run is complete! When ready, run: git reflog expire --expire=now --all && git gc --prune=now --aggressive")

  }

  def title(text: String) = s"\n$text\n" + ("-" * text.size)
}