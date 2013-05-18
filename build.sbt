organization in ThisBuild := "com.madgag"

scalaVersion in ThisBuild := "2.10.1"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature", "-language:postfixOps")

licenses in ThisBuild := Seq("GPLv3" -> url("http://www.gnu.org/licenses/gpl-3.0.html"))

homepage in ThisBuild := Some(url("https://github.com/rtyley/bfg-repo-cleaner"))

libraryDependencies in ThisBuild ++= Seq(
  "com.madgag" % "org.eclipse.jgit" % "2.99.99.0.0-UNOFFICIAL-ROBERTO-RELEASE",
  "org.specs2" %% "specs2" % "1.14" % "test"
)

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild := (
  <scm>
    <url>git@github.com:rtyley/bfg-repo-cleaner.git</url>
    <connection>scm:git:git@github.com:rtyley/bfg-repo-cleaner.git</connection>
  </scm>
    <developers>
      <developer>
        <id>rtyley</id>
        <name>Roberto Tyley</name>
        <url>https://github.com/rtyley</url>
      </developer>
    </developers>)
