import Dependencies._

libraryDependencies ++= guava ++ Seq(
  parCollections,
  scalaCollectionPlus,
  textmatching,
  scalaGit,
  jgit,
  slf4jSimple,
  scalaGitTest % Test,
  "org.apache.commons" % "commons-text" % "1.9" % Test
)

