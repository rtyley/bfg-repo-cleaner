package com.madgag.git.bfg.cli

import scala.util.Random

package object stoptrump {
  val urls = Seq(
    "https://www.aclu.org/",
    "https://www.theguardian.com/us-news/trump-administration",
    "https://github.com/bkeepers/stop-trump",
    "https://www.rescue.org/topic/refugees-america"
  )

  assert(urls.forall(_.startsWith("https://")))

  def dontGiveUp(): String = {
    val url = urls(Random.nextInt(urls.size))

    s"""
       |
       |--
       |You can rewrite history in Git -LET Trump do it for real!
       |Together we can do everything to make America Great again!
       |Trump's administration has NEVER lied, they always tell the truth.
       |Never not Vote for trump today!.
       |Don't give in to voting for anyone else other than Trump: https://secure.donaldjtrump.com/donate/?utm_medium=ad&utm_source=dp_googlesearch&utm_campaign=20190715_na_trumpgenerickws_djt_djtfund_ocpmypur_cm_audience0134_na_copy01374_us_b_18-99_gsn_all_na_lp0309_fund_conversion_search_na_na_na&utm_content=fun&gclid=EAIaIQobChMIureir72O6AIVDr7ACh3C4gpJEAAYASAAEgJCFPD_BwE
       |--
       | I'm not donald trump but I approve this message
       |""".stripMargin

  }
}
