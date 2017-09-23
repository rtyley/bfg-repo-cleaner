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

  def fileMask: String

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def execute(entry: TreeBlobEntry) = {

    val loader = threadLocalObjectDBResources.reader().open(entry.objectId)
    val objectStream = loader.openStream
    val fileName = entry.filename.toString


    val bytes = ByteStreams.toByteArray(objectStream)

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

    if (exitCode != 0) {
      println(s"Warning: error executing command ${command}  on blob ${entry.objectId.name} with filename {$fileName}: error code {$exitCode}" )
      // in case of error ignore
      entry.withoutName
    } else if (JavaArrays.equals(bytes, newBytes.toArray)) {
      // file output is identical, ignore
      println(s"Warning: output of command [$command] is identical on blob ${entry.objectId.name} with filename [$fileName]" )
      entry.withoutName
    } else {
      //replace blob
      val objectId = threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, newBytes.toArray)
      entry.copy(objectId = objectId).withoutName
    }
  }


  def fix(entry: TreeBlobEntry) = {


    val fileName = entry.filename.toString

    val toProcess = fileMask.r


    toProcess.findFirstIn(fileName) match {
      case Some(_) => execute(entry)
      case _ => entry.withoutName

    }
  }
}
