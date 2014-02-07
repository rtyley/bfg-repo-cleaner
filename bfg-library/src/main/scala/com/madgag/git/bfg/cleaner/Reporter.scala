package com.madgag.git.bfg.cleaner

import com.madgag.git._
import com.madgag.git.bfg.cleaner.protection.{ProtectedObjectCensus, ProtectedObjectDirtReport}
import com.madgag.text.Text._
import com.madgag.text.{ByteSize, Tables}
import java.text.SimpleDateFormat
import java.util.Date
import org.eclipse.jgit.diff.DiffEntry.ChangeType._
import org.eclipse.jgit.diff._
import org.eclipse.jgit.lib.FileMode._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevWalk, RevCommit}
import org.eclipse.jgit.transport.ReceiveCommand
import scala.Some
import scala.collection.convert.wrapAll._
import scala.collection.immutable.SortedMap
import scalax.file.Path
import com.madgag.git.bfg.GitUtil._

trait Reporter {

  val progressMonitor: ProgressMonitor

  def reportRefsForScan(allRefs: Traversable[Ref])(implicit objReader: ObjectReader)

  def reportRefUpdateStart(refUpdateCommands: Traversable[ReceiveCommand])

  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk)

  def reportCleaningStart(commits: Seq[RevCommit])

  def reportResults(commits: List[RevCommit], objectIdCleaner: ObjectIdCleaner)
}

class CLIReporter(repo: Repository) extends Reporter {

  lazy val reportsDir = {
    val dateString = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm").format(new Date())
    val dir = Path.fromString(repo.topDirectory.getAbsolutePath + ".bfg-report") / dateString
    dir.doCreateDirectory()
    dir
  }

  lazy val progressMonitor = new TextProgressMonitor

  def reportRefUpdateStart(refUpdateCommands: Traversable[ReceiveCommand]) {
    println(title("Updating " + plural(refUpdateCommands, "Ref")))

    val summaryTableCells = refUpdateCommands.map(update => (update.getRefName, update.getOldId.shortName, update.getNewId.shortName))

    Tables.formatTable(("Ref", "Before", "After"), summaryTableCells.toSeq).map("\t" + _).foreach(println)

    println
  }

  def reportRefsForScan(allRefs: Traversable[Ref])(implicit objReader: ObjectReader) {
    val refsByObjType = allRefs.groupBy {
      ref => objReader.open(ref.getObjectId).getType
    } withDefault Seq.empty

    refsByObjType.foreach {
      case (typ, refs) => println("Found " + refs.size + " " + Constants.typeString(typ) + "-pointing refs : " + abbreviate(refs.map(_.getName).toSeq, "...", 4).mkString(", "))
    }
  }


  // abort due to Dirty Tips on Private run - user needs to manually clean
  // warn due to Dirty Tips on Public run - it's not so serious if users publicise dirty tips.
  // if no protection
  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk) {
    println(title("Protected commits"))

    if (objectIdCleanerConfig.protectedObjectCensus.isEmpty) {
      println("You're not protecting any commits, which means the BFG will modify the contents of even *current* commits.\n\n" +
        "This isn't recommended - ideally, if your current commits are dirty, you should fix up your working copy and " +
        "commit that, check that your build still works, and only then run the BFG to clean up your history.")
    } else {
      println("These are your protected commits, and so their contents will NOT be altered:\n")

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

    ProtectedObjectDirtReport.reportsFor(objectIdCleanerConfig, objectDB).foreach {
      report =>
        val protectorRevs = objectIdCleanerConfig.protectedObjectCensus.protectorRevsByObject(report.revObject)
        val objectTitle = s" * ${report.revObject.typeString} ${report.revObject.shortName} (protected by '${protectorRevs.mkString("', '")}')"

        report.dirt match {
          case None => println(objectTitle)
          case Some(diffEntries) =>
            if (diffEntries.isEmpty) {
              println(objectTitle + " - dirty")
            } else {
              println(objectTitle + " - contains " + plural(diffEntries, "dirty file") + " : ")
              abbreviate(diffEntries.view.map(diffDetails).map(fileInfo), "...").foreach {
                dirtyFile => println("\t- " + dirtyFile)
              }
              val diffFile = protectedDirtDir / s"${report.revObject.shortName}-${protectorRevs.mkString("_")}.csv"

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

//    val dirtyReports = reports.filter(_.objectProtectsDirt)
//    if (dirtyReports.nonEmpty) {
//
//      println(s"""
//      |WARNING: The dirty content above may be removed from other commits, but as
//      |the *protected* commits still use it, it will STILL exist in your repository.
//      |
//      |Details of protected dirty content have been recorded here :
//      |
//      |${protectedDirtDir.path + protectedDirtDir.separator}
//      |
//      |If you *really* want this content gone, make a manual commit that removes it,
//      |and then run the BFG on a fresh copy of your repo.
//       """.stripMargin)
//      // TODO would like to abort here if we are cleaning 'private' data.
//    }
  }

  def changedLinesFor(edits: EditList): String = {
    edits.map {
      edit => Seq(edit.getBeginA + 1, edit.getEndA).distinct.mkString("-")
    }.mkString(";")
  }

  def reportCleaningStart(commits: Seq[RevCommit]) {
    println(title("Cleaning"))
    println("Found " + commits.size + " commits")
  }

  def reportResults(commits: List[RevCommit], objectIdCleaner: ObjectIdCleaner) {
//    def reportTreeDirtHistory() {
//
//      val dirtHistoryElements = math.max(20, math.min(60, commits.size))
//      def cut[A](xs: Seq[A], n: Int) = {
//        val avgSize = xs.size.toFloat / n
//        def startOf(unit: Int): Int = math.round(unit * avgSize)
//        (0 until n).view.map(u => xs.slice(startOf(u), startOf(u + 1)))
//      }
//      val treeDirtHistory = cut(commits, dirtHistoryElements).map {
//        case commits if commits.isEmpty => ' '
//        case commits if (commits.exists(c => objectIdCleaner.isDirty(c.getTree))) => 'D'
//        case commits if (commits.exists(objectIdCleaner.isDirty)) => 'm'
//        case _ => '.'
//      }.mkString
//      def leftRight(markers: Seq[String]) = markers.mkString(" " * (treeDirtHistory.length - markers.map(_.size).sum))
//      println(title("Commit Tree-Dirt History"))
//      println("\t" + leftRight(Seq("Earliest", "Latest")))
//      println("\t" + leftRight(Seq("|", "|")))
//      println("\t" + treeDirtHistory)
//      println("\n\tD = dirty commits (file tree fixed)")
//      println("\tm = modified commits (commit message or parents changed)")
//      println("\t. = clean commits (no changes to file tree)\n")
//
//      val firstModifiedCommit = ("First modified commit", commits.find(objectIdCleaner.isDirty).get)
//      val lastDirtyCommit = ("Last dirty commit", commits.reverse.find(c => objectIdCleaner.isDirty(c.getTree)).get)
//      val items = for ((desc, commit) <- Seq(firstModifiedCommit, lastDirtyCommit);
//                       (before, after) <- objectIdCleaner.substitution(commit)
//      ) yield (desc, before.shortName, after.shortName)
//      Tables.formatTable(("", "Before", "After"), items).map("\t" + _).foreach(println)
//    }
//
//    reportTreeDirtHistory()

    lazy val mapFile = reportsDir / "object-id-map.old-new.txt"
    lazy val cacheStatsFile = reportsDir / "cache-stats.txt"

    val changedIds = objectIdCleaner.cleanedObjectMap()

    println(s"\n\nIn total, ${changedIds.size} object ids were changed - a record of these will be written to:\n\n\t${mapFile.path}")

    mapFile.writeStrings(SortedMap[AnyObjectId, ObjectId](changedIds.toSeq: _*).view.map { case (o,n) => s"${o.name} ${n.name}"}, "\n")

    cacheStatsFile.writeStrings(objectIdCleaner.stats().seq.map(_.toString()), "\n")

    println("\nBFG run is complete!")

  }

  def title(text: String) = s"\n$text\n" + ("-" * text.size) + "\n"
}