package play.core.server.servlet30

import javax.servlet._
import javax.servlet.annotation._
import javax.servlet.http._
import java.io._
import java.util.concurrent.atomic._
import java.util.Arrays

import play.api._
import play.api.mvc._
import play.api.http._
import play.api.http.HeaderNames._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._
import play.core._
import play.core.server.servlet._
import server.Server

import scala.collection.JavaConverters._

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends play.core.server.servlet.Play2Servlet[Tuple2[AsyncContext, AsyncListener]] with Helpers {

  protected override def onBeginService(request: HttpServletRequest, response: HttpServletResponse): Tuple2[AsyncContext, AsyncListener] = {
    val asyncListener = new AsyncListener(request.toString)
    val asyncContext = request.startAsync
    asyncContext.setTimeout(-1);

    (asyncContext, asyncListener);
  }

  protected override def onFinishService(execContext: Tuple2[AsyncContext, AsyncListener]) = {
    // Nothing to do
  }

  protected override def onHttpResponseComplete(execContext: Tuple2[AsyncContext, AsyncListener]) = {
    execContext._1.complete
  }

  protected override def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
    Map.empty[String, Seq[String]] ++ request.getParameterMap.asScala.mapValues(Arrays.asList(_: _*).asScala)
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
