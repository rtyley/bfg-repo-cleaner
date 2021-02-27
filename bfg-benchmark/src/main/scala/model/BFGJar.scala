package model

import java.nio.file.Path

object BFGJar {
  def from(path: Path) = BFGJar(path, Map.empty)
}

case class BFGJar(path: Path, mavenDependencyVersions: Map[String, String])
