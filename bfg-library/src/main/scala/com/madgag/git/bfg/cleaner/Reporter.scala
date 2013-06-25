package com.madgag.git.bfg.cleaner

import org.eclipse.jgit.revwalk.{RevWalk, RevCommit}
import org.eclipse.jgit.lib._
import com.madgag.text.Tables
import com.madgag.git.bfg.GitUtil._
import com.madgag.text.Text._
import org.eclipse.jgit.transport.ReceiveCommand
import com.madgag.git._
import com.madgag.git.bfg.cleaner.protection.ProtectedObjectDirtReport
import scalax.file.Path
import scala.collection.immutable.SortedMap

trait Reporter {

  val progressMonitor: ProgressMonitor

  def reportRefsForScan(allRefs: Traversable[Ref])(implicit objReader: ObjectReader)

  def reportRefUpdateStart(refUpdateCommands: Traversable[ReceiveCommand])

  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config, objectIdCleaner: ObjectIdCleaner)(implicit revWalk: RevWalk)

  def reportCleaningStart(commits: Seq[RevCommit])

  def reportResults(commits: List[RevCommit], objectIdCleaner: ObjectIdCleaner)
}

class CLIReporter(repo: Repository) extends Reporter {

  lazy val reportsDir = {
    val dir = Path.fromString(repo.getDirectory.getAbsolutePath + ".bfg-report")
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
  def reportObjectProtection(objectIdCleanerConfig: ObjectIdCleaner.Config, objectIdCleaner: ObjectIdCleaner)(implicit revWalk: RevWalk) {
    println(title("Protected commits"))

    if (objectIdCleanerConfig.protectedObjectCensus.isEmpty) {
      println("You're not protecting any commits, which means the BFG will modify the contents of even *current* commits.\n\n" +
        "This isn't recommended - ideally, if your current commits are dirty, you should fix up your working copy and " +
        "commit that, check that your build still works, and only then run the BFG to clean up your history.")
    } else {
      println("These are your latest commits, and so their contents will NOT be altered:\n")

      val reports = objectIdCleanerConfig.protectedObjectCensus.protectorRevsByObject.map {
        case (protectedRevObj, refNames) =>
          implicit val reader = revWalk.getObjectReader

          val originalContentObject = treeOrBlobPointedToBy(protectedRevObj).merge
          val replacementTreeOrBlob = objectIdCleaner.uncachedClean.replacement(originalContentObject)
          ProtectedObjectDirtReport(protectedRevObj, originalContentObject, replacementTreeOrBlob)
      }.toList

      protection.Reporter.reportProtectedCommitsAndTheirDirt(reports, objectIdCleanerConfig)
    }
  }

  def reportCleaningStart(commits: Seq[RevCommit]) {
    println(title("Cleaning"))
    println("Found " + commits.size + " commits")
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
      println(title("Commit Tree-Dirt History"))
      println("\t" + leftRight(Seq("Earliest", "Latest")))
      println("\t" + leftRight(Seq("|", "|")))
      println("\t" + treeDirtHistory)
      println("\n\tD = dirty commits (file tree fixed)")
      println("\tm = modified commits (commit message or parents changed)")
      println("\t. = clean commits (no changes to file tree)\n")

      val firstModifiedCommit = ("First modified commit", commits.find(objectIdCleaner.isDirty).get)
      val lastDirtyCommit = ("Last dirty commit", commits.reverse.find(c => objectIdCleaner.isDirty(c.getTree)).get)
      val items = for ((desc, commit) <- Seq(firstModifiedCommit, lastDirtyCommit);
                       (before, after) <- objectIdCleaner.substitution(commit)
      ) yield (desc, before.shortName, after.shortName)
      Tables.formatTable(("", "Before", "After"), items).map("\t" + _).foreach(println)
    }

    reportTreeDirtHistory()

    lazy val mapFile = reportsDir / "object-id-map.old-new.txt"

    val changedIds = objectIdCleaner.cleanedObjectMap()

    println(s"\n\nIn total, ${changedIds.size} object ids were changed - a record of these will be written to:\n\n\t${mapFile.path}")

    mapFile.writeStrings(SortedMap[AnyObjectId, ObjectId](changedIds.toSeq: _*).view.map { case (o,n) => s"${o.name} ${n.name}"}, "\n")

    println("\nBFG run is complete!")

  }

  def title(text: String) = s"\n$text\n" + ("-" * text.size) + "\n"
}