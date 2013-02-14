/*
 * Copyright (c) 2012 Roberto Tyley
 *
 * This file is part of 'BFG Repo-Cleaner' - a tool for removing large
 * or troublesome blobs from Git repositories.
 *
 * BFG Repo-Cleaner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BFG Repo-Cleaner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/ .
 */

package com.madgag.git.bfg.cli

import com.madgag.git.bfg.cleaner._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{Constants, Repository, TextProgressMonitor}
import scala.collection.JavaConversions._
import com.madgag.git.bfg.GitUtil._
import org.eclipse.jgit.revwalk.RevWalk

object Main extends App {
  def hasBeenProcessedByBFGBefore(repo: Repository): Boolean = {
    // This method just checks the tips of all refs - a good-enough indicator for our purposes...
    implicit val revWalk = new RevWalk(repo)
    implicit val objectReader = revWalk.getObjectReader

    repo.getAllRefs.values.map(_.getObjectId).filter(_.open.getType == Constants.OBJ_COMMIT)
      .map(_.asRevCommit).exists(_.getFooterLines(FormerCommitFooter.Key).nonEmpty)
  }

  if (args.isEmpty) {
    CLIConfig.parser.showUsage
  } else {
    tweakStaticJGitConfig

    CLIConfig.parser.parse(args, CLIConfig()) map {
      config =>
        if (config.gitdir.isEmpty) {
          CLIConfig.parser.showUsage
          Console.err.println("Aborting : " + config.repoLocation + " is not a valid Git repository.\n")
        } else {
          implicit val repo = config.repo

          println("\nUsing repo : " + repo.getDirectory.getAbsolutePath + "\n")

          // do this before implicitly initiating big-blob search
          if (hasBeenProcessedByBFGBefore(repo)) {
            println("\nThis repo has been processed by The BFG before! Will prune repo before proceeding - to avoid unnecessary cleaning work on unused objects.")
            new Git(repo).gc.setProgressMonitor(new TextProgressMonitor()).call()
            println("Completed prune of old objects - will now proceed with the main job!\n")
          }

          if (config.definesNoWork) {
            Console.err.println("Please specify tasks for The BFG :")
            CLIConfig.parser.showUsage
          } else {
            println("Found " + config.objectProtection.fixedObjectIds.size + " objects to protect")

            RepoRewriter.rewrite(repo, config.objectIdCleanerConfig)
          }
        }
    }
  }

}