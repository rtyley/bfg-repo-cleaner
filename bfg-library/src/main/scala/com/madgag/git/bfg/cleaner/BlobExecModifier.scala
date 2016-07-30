package com.madgag.git.bfg.cleaner

import com.google.common.io.ByteStreams
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.ThreadLocalObjectDatabaseResources
import java.util.{Arrays => JavaArrays}
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import scala.collection.JavaConversions._
import scalax.io._
import scalax.io.managed._
import java.io._
import scala.sys.process._
import scala.sys.process.ProcessIO
import scala.io.Source
import scala.collection.mutable.ArrayBuffer

trait BlobExecModifier extends TreeBlobModifier {

  def command: String

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def execute(entry: TreeBlobEntry) = {

    val loader = threadLocalObjectDBResources.reader().open(entry.objectId)
    val objectStream = loader.openStream
    val fileName = entry.filename.toString


    val bytes = ByteStreams.toByteArray(objectStream)

//    println(s"${entry.objectId.name}: 00 To execute : ${command} Length: ${bytes.length} on file ${entry.filename}")

    val newBytes = ArrayBuffer[Byte]()

    def readJob(in: InputStream) {

      newBytes.appendAll(Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray)
      in.close();

    }

    def writeJob(out: OutputStream) {
      out.write(bytes)
      out.close()
    }

    val io = new ProcessIO(
      writeJob,
      readJob,
      _=> (),
      false
    )

    val pb = Process(command, None, "BFG_BLOB" -> entry.objectId.name, "BFG_FILENAME" -> fileName)
    val proc = pb.run(io)
    val exitCode = proc.exitValue

//    println(s"${entry.objectId.name}: 20 {$fileName} Waiting for BFG_BLOB Length: ${bytes.length} New bytes: ${newBytes.length} ExitCode ${exitCode}")

    //    if(JavaArrays.equals(bytes, newBytes)) {


    if (exitCode != 0 || JavaArrays.equals(bytes, newBytes.toArray)) {
//      println(s"${entry.objectId.name}: 31 Nothing to do {$fileName}" )
      entry.withoutName
    } else {
//      println(s"${entry.objectId.name}: 35 to replace {$fileName}" )

      val objectId = threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, newBytes.toArray)
      entry.copy(objectId = objectId).withoutName
    }
  }


  def fix(entry: TreeBlobEntry) = {

    val fileName = entry.filename.toString
    //    val toProcess = "^net(.+)\\.[ch]$".r
    val toProcess = "(.+)\\.[ch]$".r

    fileName match {
      case toProcess(_) => execute(entry)
      case _ => entry.withoutName

    }
  }
}
