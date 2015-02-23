package com.madgag.git.bfg.cleaner

import com.google.common.io.ByteStreams
import com.madgag.git.bfg.model.TreeBlobEntry
import com.madgag.git.ThreadLocalObjectDatabaseResources
import java.util.{Arrays => JavaArrays}
import org.eclipse.jgit.lib.Constants.OBJ_BLOB
import scala.collection.JavaConversions._

trait BlobExecModifier extends TreeBlobModifier {

  def command: String

  val threadLocalObjectDBResources: ThreadLocalObjectDatabaseResources

  def fix(entry: TreeBlobEntry) = {
    val loader = threadLocalObjectDBResources.reader().open(entry.objectId)
    val objectStream = loader.openStream
    val variables = System.getenv().map { case (key, value) => s"$key=$value" }.toSeq :+
      s"BFG_BLOB=${entry.objectId.name}"
    val process = Runtime.getRuntime.exec(command, variables.toArray)

    val bytes = ByteStreams.toByteArray(objectStream)
    objectStream.close()
    process.getOutputStream.write(bytes)
    process.getOutputStream.close()

    ByteStreams.copy(process.getErrorStream, System.err)
    process.getErrorStream.close()

    val newBytes = ByteStreams.toByteArray(process.getInputStream)
    process.getInputStream.close()

    val exitCode = process.waitFor()
    if(exitCode != 0) {
      throw new RuntimeException(s"Process exited with code $exitCode")
    }

    if(JavaArrays.equals(bytes, newBytes)) {
      entry.withoutName
    } else {
      val objectId = threadLocalObjectDBResources.inserter().insert(OBJ_BLOB, newBytes)
      entry.copy(objectId = objectId).withoutName
    }
  }

}
