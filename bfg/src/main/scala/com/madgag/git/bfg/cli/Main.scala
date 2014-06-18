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

import com.madgag.git._
import com.madgag.git.bfg.cleaner._
import com.madgag.git.bfg.GitUtil._

object Main extends App {

  if (args.isEmpty) {
    CLIConfig.parser.showUsage
  } else {

    CLIConfig.parser.parse(args, CLIConfig()) map {
      config =>

        tweakStaticJGitConfig(config.massiveNonFileObjects)

        if (config.gitdir.isEmpty) {
          CLIConfig.parser.showUsage
          Console.err.println(s"Aborting : ${config.repoLocation} is not a valid Git repository.\n")
        } else {
          implicit val repo = config.repo

          implicit val jl = config.jl
          val logger = jl.logContext.getLogger("main")

          logger.info(s"\nUsing repo : ${repo.getDirectory.getAbsolutePath}\n")

          // do this before implicitly initiating big-blob search
          if (hasBeenProcessedByBFGBefore(repo)) {
            logger.warn("\nThis repo has been processed by The BFG before! Will prune repo before proceeding - to avoid unnecessary cleaning work on unused objects...")
            repo.git.gc.call()
            logger.info("Completed prune of old objects - will now proceed with the main job!\n")
          }

          if (config.definesNoWork) {
            logger.error("Please specify tasks for The BFG :")
            CLIConfig.parser.showUsage
          } else {
            logger.info(s"Found ${config.objectProtection.fixedObjectIds.size} objects to protect")

            new RepoRewriter(repo, config.objectIdCleanerConfig).rewrite()
          }
        }
    }
  }

}