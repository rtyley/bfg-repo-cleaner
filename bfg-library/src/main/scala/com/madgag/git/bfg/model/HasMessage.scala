package com.madgag.git.bfg.model


trait HasMessage[N] {
  def updateMessageWith(cleaner: String => String): N
}
