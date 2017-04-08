package com.madgag.git.bfg.cleaner

import java.io.FileInputStream

import com.google.common.io.{ByteStreams, FileBackedOutputStream}
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.{ThreadLocalObjectDatabaseResources, _}
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import org.eclipse.jgit.lib.ObjectId

import scala.sys.process._
import scalax.file.ImplicitConversions._
import scalax.file.Path.createTempDirectory

trait BlobExecModifier extends TreeBlobModifier {

  def command: String

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def fix(entry: TreeBlobEntry) = {
    println(s"Starting BlobExecModifier with '$command' on "+entry)
//    val idFromFile = operateOnFileFrom(entry)
//    println(s"idFromFile=$idFromFile")

    val idFromStream= operateOnStreamFrom(entry).withoutName

    println(s"idFromStream=$idFromStream")
    val newFile = new String(idFromStream._2.open(threadLocalObjectDBResources.reader()).getBytes)
    println(s"newFile=$newFile")

    idFromStream
  }

  private def operateOnStreamFrom(entry: TreeBlobEntry):TreeBlobEntry = {

    implicit lazy val reader = threadLocalObjectDBResources.reader()

    val processOutputStream = new FileBackedOutputStream(16 * 1024)

    val exitCode = Process(command, None, paramsFor(entry): _*).run(new ProcessIO(
      in => {
        entry.objectId.open.copyTo(in)
        in.close()
      },
      out => BasicIO.transferFully(out, processOutputStream),
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

  def paramsFor(entry: TreeBlobEntry) = Seq(
    "BFG_BLOB_ID" -> entry.objectId.name,
    "BFG_BLOB_FILENAME" -> entry.filename.string
  )


  private def operateOnFileFrom(entry: TreeBlobEntry):TreeBlobEntry = {
    implicit lazy val reader = threadLocalObjectDBResources.reader()

    val tempDir = createTempDirectory(s"bfg.exec.${entry.objectId.name}")

    val tempFile = tempDir / entry.filename.string

    for {
      outStream <- tempFile.outputStream()
    } entry.objectId.open.copyTo(outStream)


    Process(command, tempDir, paramsFor(entry): _*).!

    val newObjectId = (for {
      newSize <- tempFile.size
    } yield tempFile.inputStream.acquireAndGet { fs: FileInputStream =>
      threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, newSize, fs)
    }).getOrElse(ObjectId.zeroId)

    tempDir.deleteRecursively()

    entry.copy(objectId = newObjectId)
  }
}
