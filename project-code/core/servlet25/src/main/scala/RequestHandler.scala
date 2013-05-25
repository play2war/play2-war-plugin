package play.core.server.servlet25

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.core.server.servlet.Play2GenericServletRequestHandler
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse
import java.util.concurrent.{TimeoutException, ArrayBlockingQueue, TimeUnit}

class Play2Servlet25RequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse)
  extends Play2GenericServletRequestHandler(servletRequest, Option(servletResponse))
  with Helpers {

  val queue = new ArrayBlockingQueue[Boolean](1)

  protected override def onFinishService() = {
    val hasBeenComplete = queue.poll(Play2Servlet.syncTimeout, TimeUnit.MILLISECONDS)

    if (hasBeenComplete == null) {
      throw new TimeoutException("This request was timed out after " + Play2Servlet.syncTimeout + " ms")
    }
  }

  protected override def onHttpResponseComplete() = {
    queue.put(Boolean.TRUE)
  }

  protected override def getHttpRequest(): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[java.io.InputStream] = {
        Option(servletRequest.getInputStream)
      }
    }
  }

  protected override def getHttpResponse(): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[java.io.OutputStream] = {
        Option(servletResponse.getOutputStream)
      }

      def getHttpServletResponse: Option[HttpServletResponse] = {
        Option(servletResponse)
      }
    }
  }

}