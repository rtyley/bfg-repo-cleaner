package com.madgag.git.bfg.model

import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.lib.{TagBuilder, PersonIdent}

object TagNode {
  def apply(t: RevTag): TagNode = TagNode(t.getTaggerIdent, t.getFullMessage)
}

case class TagNode(tagger: PersonIdent, message: String) extends HasMessage[TagNode] {

  def updateMessageWith(cleaner: String => String): TagNode = copy(message = cleaner(message))

  def update(tagBuilder: TagBuilder) {
    tagBuilder.setTagger(tagger)
    tagBuilder.setMessage(message)
  }
}