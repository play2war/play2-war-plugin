/*
 * Copyright 2013 Damien Lecan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.core.server.servlet30

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

import javax.servlet.AsyncEvent
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.core.server.servlet.Play2GenericServletRequestHandler
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse

class Play2Servlet30RequestHandler(servletRequest: HttpServletRequest)
  extends Play2GenericServletRequestHandler(servletRequest, None)
  with Helpers {

  val asyncListener = new AsyncListener(servletRequest.toString)

  val asyncContext = servletRequest.startAsync
  asyncContext.setTimeout(play.core.server.servlet30.Play2Servlet.asyncTimeout)

  protected override def onFinishService() = {
    // Nothing to do
  }

  protected override def onHttpResponseComplete() = {
    asyncContext.complete
  }

  protected override def getHttpRequest(): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[InputStream] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getRequest.getInputStream)
        } else {
          None
        }
      }
    }
  }

  protected override def getHttpResponse(): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[OutputStream] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getResponse.getOutputStream)
        } else {
          None
        }
      }

      def getHttpServletResponse: Option[HttpServletResponse] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getResponse.asInstanceOf[HttpServletResponse])
        } else {
          None
        }
      }
    }
  }

  private def asyncContextAvailable(asyncListener: AsyncListener) = {
    !asyncListener.withError.get && !asyncListener.withTimeout.get
  }

}

private[servlet30] class AsyncListener(val requestId: String) extends javax.servlet.AsyncListener {

  val withError = new AtomicBoolean(false)

  val withTimeout = new AtomicBoolean(false)

  // Need a default constructor for JBoss
  def this() = this("Unknown request id")

  override def onComplete(event: AsyncEvent): Unit = {
    // Logger("play").trace("onComplete: " + requestId)
    // Nothing
  }

  override def onError(event: AsyncEvent): Unit = {
    withError.set(true)
    Logger("play").error("Error asynchronously received for request: " + requestId, event.getThrowable)
  }

  override def onStartAsync(event: AsyncEvent): Unit = {} // Nothing

  override def onTimeout(event: AsyncEvent): Unit = {
    withTimeout.set(true)
    Logger("play").warn("Timeout asynchronously received for request: " + requestId)
  }
}
