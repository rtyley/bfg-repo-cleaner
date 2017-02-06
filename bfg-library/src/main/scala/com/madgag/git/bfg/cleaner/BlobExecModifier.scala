package com.madgag.git.bfg.cleaner

import java.io.{File, FileInputStream}
import java.nio.file.Files
import java.security.DigestOutputStream

import scala.sys.process._
import com.google.common.io.{ByteStreams, FileBackedOutputStream}
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.ThreadLocalObjectDatabaseResources
import java.util.{Arrays => JavaArrays}

import org.eclipse.jgit.lib.Constants.OBJ_BLOB

import scala.collection.JavaConversions._
import com.madgag.git._
import org.eclipse.jgit.lib.ObjectId

import scala.concurrent.Promise
import scala.sys.process
import scalax.file.ImplicitConversions._
import scalax.file.Path.createTempDirectory
import scalax.io.JavaConverters._
import scalax.io.managed.InputStreamResource

trait BlobExecModifier extends TreeBlobModifier {

  def command: String

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def fix(entry: TreeBlobEntry) = {
    operateOnFileFrom(entry)

    operateOnStreamFrom(entry).withoutName
  }

  private def operateOnStreamFrom(entry: TreeBlobEntry):TreeBlobEntry = {

    implicit lazy val reader = threadLocalObjectDBResources.reader()
    //    val variables = System.getenv().map { case (key, value) => s"$key=$value" }.toSeq :+
    //      s"BFG_BLOB=${entry.objectId.name}"
    //    val process = Runtime.getRuntime.exec(command, variables.toArray)

    val processOutputStream = new FileBackedOutputStream(16 * 1024)

    val exitCode = Process(command, None,
      "BFG_BLOB_ID" -> entry.objectId.name,
      "BFG_BLOB_FILENAME" -> entry.filename.string
    ).run(new ProcessIO(
      in => entry.objectId.open.copyTo(in),
      out => ByteStreams.copy(out, processOutputStream),
      BasicIO.toStdErr
    )).exitValue()

    if (exitCode != 0) {
      throw new RuntimeException(s"Process exited with code $exitCode")
    }

    val processOutput = processOutputStream.asByteSource()
    val objectId = threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, processOutput.size(), processOutput.openStream())
    processOutputStream.reset()

    entry.copy(objectId = objectId)
  }

  private def operateOnFileFrom(entry: TreeBlobEntry):TreeBlobEntry = {
    implicit lazy val reader = threadLocalObjectDBResources.reader()

    val tempDir = createTempDirectory(s"bfg.exec.${entry.objectId.name}")

    val tempFile = tempDir / entry.filename.string

    for {
      outStream <- tempFile.outputStream()
    } entry.objectId.open.copyTo(outStream)


    Process(command, tempDir,
      "BFG_BLOB_ID" -> entry.objectId.name,
      "BFG_BLOB_FILENAME" -> entry.filename.string
    ).!

    val newObjectId = (for {
      newSize <- tempFile.size
    } yield tempFile.inputStream.acquireAndGet { fs: FileInputStream =>
      threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, newSize, fs)
    }).getOrElse(ObjectId.zeroId)

    tempDir.delete(true)

    entry.copy(objectId = newObjectId)
  }
}
