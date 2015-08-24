package lib

import com.madgag.compress.CompressUtil._

import scalax.file.ImplicitConversions._
import scalax.file.Path
import scalax.file.defaultfs.DefaultPath

class RepoExtractor(scratchDir: DefaultPath) {

  val repoDir = scratchDir / "repo.git"

  def extractRepoFrom(zipPath: Path) = {
    repoDir.deleteRecursively(force = true)

    repoDir.createDirectory()

    println(s"Extracting repo to ${repoDir.toAbsolute.path}")

    zipPath.inputStream.acquireFor { stream => unzip(stream, repoDir) }

    repoDir
  }
}
