package lib

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE
import com.madgag.compress.CompressUtil._

import java.nio.file.{Files, Path}
import scala.util.Using

class RepoExtractor(scratchDir: Path) {

  val repoDir = scratchDir.resolve( "repo.git")

  def extractRepoFrom(zipPath: Path) = {
    if (Files.exists(repoDir)) MoreFiles.deleteRecursively(repoDir, ALLOW_INSECURE)

    Files.createDirectories(repoDir)

    println(s"Extracting repo to ${repoDir.toAbsolutePath}")

    Using(Files.newInputStream(zipPath)) {
      stream => unzip(stream, repoDir.toFile)
    }

    repoDir
  }
}
