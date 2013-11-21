package com.madgag.git.bfg.cleaner

import com.madgag.git.ThreadLocalObjectDatabaseResources
import org.eclipse.jgit.lib.ObjectId

class TagNodeCleanerKit(val threadLocalResources: ThreadLocalObjectDatabaseResources, val mapper: Cleaner[ObjectId])
  extends NodeMessageCleanerKit
