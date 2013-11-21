package com.madgag.git.bfg.cleaner

import com.madgag.git.ThreadLocalObjectDatabaseResources
import org.eclipse.jgit.lib.ObjectId
import com.madgag.git.bfg.model.HasMessage

object NodeCleaner {
  def chain[N, K](cleaners: Seq[NodeCleaner[N, K]]) = new NodeCleaner[N,K] {
    def fixer(kit: K) = Function.chain(cleaners.map(_.fixer(kit)))
  }
}

trait NodeCleaner[N, K] {
  def fixer(kit: K): Cleaner[N]
}

trait NodeMessageCleanerKit {
  val threadLocalResources: ThreadLocalObjectDatabaseResources
  val mapper: Cleaner[ObjectId]
}

class NodeMessageCleaner[N <: HasMessage[N], K <: NodeMessageCleanerKit](cleaner: Cleaner[String]) extends NodeCleaner[N, K] {
  override def fixer(kit: K):Cleaner[N] = _.updateMessageWith(cleaner)
}