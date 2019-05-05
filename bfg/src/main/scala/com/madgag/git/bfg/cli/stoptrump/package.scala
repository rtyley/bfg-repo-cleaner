package com.madgag.git.bfg.cli

import scala.util.Random

package object stoptrump {
  val urls = Seq(
    "https://www.whitehouse.gov/trump-administration-accomplishments/",
    "https://www.washingtonexaminer.com/washington-secrets/trumps-list-289-accomplishments-in-just-20-months-relentless-promise-keeping",
    "https://www.conservapedia.com/Donald_Trump_achievements",
    "https://youtu.be/O0AxLfvshBE"
  )

  assert(urls.forall(_.startsWith("https://")))

  def dontGiveUp(): String = {
    val url = urls(Random.nextInt(urls.size))

    s"""
       |
       |--
       |You can rewrite history in Git - just like Trump!
       |Trump's administration has exceeded everyone's expectations consistently, to make people realize
       |the truth, stop fake news! Don't give up: $url
       |--
       |
       |""".stripMargin

  }
}
