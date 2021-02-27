package com.madgag.git.bfg.cleaner

import com.google.common.io.Files.asCharSink
import com.madgag.collection.concurrent.ConcurrentMultiMap
import com.madgag.git._
import com.madgag.git.bfg.cleaner.Reporter.dump
import com.madgag.git.bfg.cleaner.protection.{ProtectedObjectCensus, ProtectedObjectDirtReport}
import com.madgag.git.bfg.model.FileName
import com.madgag.text.Text._
import com.madgag.text.{ByteSize, Tables, Text}
import org.eclipse.jgit.diff.DiffEntry.ChangeType._
import org.eclipse.jgit.diff._
import org.eclipse.jgit.lib.FileMode._
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.transport.ReceiveCommand

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters._


object Reporter {
  def dump(path: Path, iter: Iterable[String]): Unit = {
    val sink = asCharSink(path.toFile, UTF_8)

    sink.writeLines(iter.asJava, "\n")
  }
}

trait Reporter {

  val progressMonitor: ProgressMonitor

  def reportRefsForScan(allRefs: Iterable[Ref])(implicit objReader: ObjectReader): Unit

  def reportRefUpdateStart(refUpdateCommands: Iterable[ReceiveCommand]): Unit

  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk): Unit

  def reportCleaningStart(commits: Seq[RevCommit]): Unit

  def reportResults(commits: Seq[RevCommit], objectIdCleaner: ObjectIdCleaner): Unit
}

class CLIReporter(repo: Repository) extends Reporter {

  lazy val reportsDir: Path = {
    val now = ZonedDateTime.now()

    val topDirPath = repo.topDirectory.toPath.toAbsolutePath

    val reportsDir = topDirPath.resolveSibling(s"${topDirPath.getFileName}.bfg-report")

    val dateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss")

    val dir = reportsDir.resolve(now.format(dateFormatter)).resolve(now.format(timeFormatter))

    createDirectories(dir)
    dir
  }

  lazy val progressMonitor = new TextProgressMonitor

  def reportRefUpdateStart(refUpdateCommands: Iterable[ReceiveCommand]): Unit = {
    println(title("Updating " + plural(refUpdateCommands, "Ref")))

    val summaryTableCells = refUpdateCommands.map(update => (update.getRefName, update.getOldId.shortName, update.getNewId.shortName))

    Tables.formatTable(("Ref", "Before", "After"), summaryTableCells.toSeq).map("\t" + _).foreach(println)

    println()
  }

  def reportRefsForScan(allRefs: Iterable[Ref])(implicit objReader: ObjectReader): Unit = {
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
  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk): Unit = {
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

  def reportProtectedCommitsAndTheirDirt(objectIdCleanerConfig: ObjectIdCleaner.Config)(implicit objectDB: ObjectDatabase, revWalk: RevWalk): Unit = {
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

    val protectedDirtDir = reportsDir.resolve("protected-dirt")
    createDirectories(protectedDirtDir)

    val reports = ProtectedObjectDirtReport.reportsFor(objectIdCleanerConfig, objectDB)

    reports.foreach {
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

              val protectorRefsFileNameSafe: String = protectorRevs.mkString("_").replace(
                protectedDirtDir.getFileSystem.getSeparator,
                "-"
              )
              val diffFile = protectedDirtDir.resolve(s"${report.revObject.shortName}-$protectorRefsFileNameSafe.csv")

              dump(diffFile, diffEntries.map {
                diffEntry =>
                  val de = diffDetails(diffEntry)

                  val modifiedLines = if (diffEntry.getChangeType == MODIFY) diffEntry.editList.map(changedLinesFor) else None

                  val elems = Seq(de.id.name, diffEntry.getChangeType.name, de.mode.name, de.path, de.size.getOrElse(""), modifiedLines.getOrElse(""))

                  elems.mkString(",")
              })
              }
            }
        }

    val dirtyReports = reports.filter(_.objectProtectsDirt)
    if (dirtyReports.nonEmpty) {

      println(s"""
      |WARNING: The dirty content above may be removed from other commits, but as
      |the *protected* commits still use it, it will STILL exist in your repository.
      |
      |Details of protected dirty content have been recorded here :
      |
      |${protectedDirtDir.toAbsolutePath.toString + protectedDirtDir.getFileSystem.getSeparator}
      |
      |If you *really* want this content gone, make a manual commit that removes it,
      |and then run the BFG on a fresh copy of your repo.
       """.stripMargin)
      // TODO would like to abort here if we are cleaning 'private' data.
    }
  }

  def changedLinesFor(edits: EditList): String = {
    edits.asScala.map {
      edit => Seq(edit.getBeginA + 1, edit.getEndA).distinct.mkString("-")
    }.mkString(";")
  }

  def reportCleaningStart(commits: Seq[RevCommit]): Unit = {
    println(title("Cleaning"))
    println("Found " + commits.size + " commits")
  }

  def reportResults(commits: Seq[RevCommit], objectIdCleaner: ObjectIdCleaner): Unit = {
    def reportTreeDirtHistory(): Unit = {

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
      println(title("Commit Tree-Dirt History"))
      println("\t" + leftRight(Seq("Earliest", "Latest")))
      println("\t" + leftRight(Seq("|", "|")))
      println("\t" + treeDirtHistory)
      println("\n\tD = dirty commits (file tree fixed)")
      println("\tm = modified commits (commit message or parents changed)")
      println("\t. = clean commits (no changes to file tree)\n")

      val firstModifiedCommit = commits.find(objectIdCleaner.isDirty).map(_ -> "First modified commit")
      val lastDirtyCommit = commits.reverse.find(c => objectIdCleaner.isDirty(c.getTree)).map(_ -> "Last dirty commit")
      val items = for {
        (commit, desc) <- firstModifiedCommit ++ lastDirtyCommit
        (before, after) <- objectIdCleaner.substitution(commit)
      } yield (desc, before.shortName, after.shortName)
      Tables.formatTable(("", "Before", "After"), items.toSeq).map("\t" + _).foreach(println)
    }

    reportTreeDirtHistory()

    lazy val mapFile: Path = reportsDir.resolve("object-id-map.old-new.txt")
    lazy val cacheStatsFile: Path = reportsDir.resolve("cache-stats.txt")

    val changedIds = objectIdCleaner.cleanedObjectMap()

    def reportFiles[FI](
        fileData: ConcurrentMultiMap[FileName, FI],
        actionType: String,
        tableTitles: Product
      )(f: ((FileName,Set[FI])) => Product)(fi: FI => Seq[String]): Unit = {
      implicit val fileNameOrdering = Ordering[String].on[FileName](_.string)

      val dataByFilename = SortedMap[FileName, Set[FI]](fileData.toMap.toSeq: _*)
      if (dataByFilename.nonEmpty) {
        println(title(s"$actionType files"))
        Tables.formatTable(tableTitles, dataByFilename.map(f).toSeq).map("\t" + _).foreach(println)

        val actionFile = reportsDir.resolve(s"${actionType.toLowerCase}-files.txt")

        dump(actionFile, dataByFilename.flatMap {
          case (filename, changes) => changes.map(fi.andThen(fid => (fid :+ filename).mkString(" ")))
        })
      }
    }

    reportFiles(objectIdCleaner.changesByFilename, "Changed", ("Filename", "Before & After")) {
      case (filename, changes) => (filename, Text.abbreviate(changes.map {case (oldId, newId) => oldId.shortName+" ⇒ "+newId.shortName}, "...").mkString(", "))
    } { case (oldId, newId) => Seq(oldId.name, newId.name) }

    implicit val reader = objectIdCleaner.threadLocalResources.reader()

    reportFiles(objectIdCleaner.deletionsByFilename, "Deleted", ("Filename", "Git id")) {
      case (filename, oldIds) => (filename, Text.abbreviate(oldIds.map(oldId => oldId.shortName + oldId.sizeOpt.map(size => s" (${ByteSize.format(size)})").mkString), "...").mkString(", "))
    } { oldId => Seq(oldId.name, oldId.sizeOpt.mkString) }

    println(s"\n\nIn total, ${changedIds.size} object ids were changed. Full details are logged here:\n\n\t$reportsDir")

    dump(mapFile,SortedMap[AnyObjectId, ObjectId](changedIds.toSeq: _*).view.map { case (o,n) => s"${o.name} ${n.name}"})

    dump(cacheStatsFile,objectIdCleaner.stats().map(_.toString()))

    println("\nBFG run is complete! When ready, run: git reflog expire --expire=now --all && git gc --prune=now --aggressive")

  }

  def title(text: String) = s"\n$text\n" + ("-" * text.size) + "\n"
}