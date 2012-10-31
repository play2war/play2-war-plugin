package play.core.server.servlet30

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

import javax.servlet.AsyncContext
import javax.servlet.AsyncEvent
import javax.servlet.annotation.WebListener
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.core.server.servlet.GenericPlay2Servlet
import play.core.server.servlet.Play2WarServer
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse

object Play2Servlet {
  val asyncTimeout = Play2WarServer.configuration.getInt("servlet30.asynctimeout").getOrElse(-1)
  Logger("play").debug("Async timeout for HTTP requests: " + asyncTimeout + " seconds")
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends GenericPlay2Servlet[Tuple2[AsyncContext, AsyncListener]] with Helpers {

  protected override def onBeginService(request: HttpServletRequest, response: HttpServletResponse): Tuple2[AsyncContext, AsyncListener] = {
    val asyncListener = new AsyncListener(request.toString)
    val asyncContext = request.startAsync
    asyncContext.setTimeout(play.core.server.servlet30.Play2Servlet.asyncTimeout);

    (asyncContext, asyncListener);
  }

  protected override def onFinishService(execContext: Tuple2[AsyncContext, AsyncListener]) = {
    // Nothing to do
  }

  protected override def onHttpResponseComplete(execContext: Tuple2[AsyncContext, AsyncListener]) = {
    execContext._1.complete
  }

  protected override def getHttpRequest(execContext: Tuple2[AsyncContext, AsyncListener]): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[InputStream] = {
        if (asyncContextAvailable(execContext._2)) {
          Option(execContext._1.getRequest.getInputStream)
        } else {
          None
        }
      }
    }
  }

  protected override def getHttpResponse(execContext: Tuple2[AsyncContext, AsyncListener]): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[OutputStream] = {
        if (asyncContextAvailable(execContext._2)) {
          Option(execContext._1.getResponse.getOutputStream)
        } else {
          None
        }
      }

      def getHttpServletResponse: Option[HttpServletResponse] = {
        if (asyncContextAvailable(execContext._2)) {
          Option(execContext._1.getResponse.asInstanceOf[HttpServletResponse])
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

  override def onComplete(event: AsyncEvent) {
    // Logger("play").trace("onComplete: " + requestId)
    // Nothing
  }

  override def onError(event: AsyncEvent) {
    withError.set(true)
    Logger("play").error("Error asynchronously received for request: " + requestId, event.getThrowable)
  }

  override def onStartAsync(event: AsyncEvent) = {} // Nothing

  override def onTimeout(event: AsyncEvent) {
    withTimeout.set(true)
    Logger("play").warn("Timeout asynchronously received for request: " + requestId)
  }
}
