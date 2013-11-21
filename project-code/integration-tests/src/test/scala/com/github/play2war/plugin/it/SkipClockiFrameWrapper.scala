package com.github.play2war.plugin.it

import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.util._
import scala.collection.JavaConverters._

class SkipClockiFrameWrapper(val webClient: WebClient) extends WebConnectionWrapper(webClient) {

  val PATTERN_TO_SKIP = "clock"

  override def getResponse(webRequest: WebRequest): WebResponse = {
    val url = webRequest.getUrl
    val path = url.getPath

    if (path.contains(PATTERN_TO_SKIP)) {

      println("Skip clock: " + path)

      val body: Array[Byte] = Array()
      val headers: List[NameValuePair] = List()
      val wrd = new WebResponseData(body, 200, "OK", headers.asJava)
      new WebResponse(wrd, webRequest, 10)
    } else {
      super.getResponse(webRequest)
    }
  }
}
