import Dependencies.*

libraryDependencies ++= guava ++ Seq(
  parCollections,
  scalaCollectionPlus,
  textmatching,
  scalaGit,
  jgit,
  slf4jSimple,
  lineSplitting,
  scalaGitTest % Test,
  "org.apache.commons" % "commons-text" % "1.13.0" % Test
)

