package model

import scalax.file.Path

object BFGJar {
  def from(path: Path) = BFGJar(path, Map.empty)
}

case class BFGJar(path: Path, mavenDependencyVersions: Map[String, String])
