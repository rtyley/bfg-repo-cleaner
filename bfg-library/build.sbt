import Dependencies._

libraryDependencies ++= guava ++ Seq(
  parCollections,
  scalaCollectionPlus,
  textmatching,
  scalaGit,
  jgit,
  slf4jSimple,
  scalaGitTest % Test
)

