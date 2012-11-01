package play.core.server.servlet25

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.core.server.servlet.Play2GenericServletRequestHandler
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse

class Play2Servlet25RequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse)
  extends Play2GenericServletRequestHandler(servletRequest, servletResponse)
  with Helpers {

  val lock = new Object();

  protected override def onFinishService() = {
    lock.synchronized {
      lock.wait(Play2Servlet.syncTimeout)
    }
  }

  protected override def onHttpResponseComplete() = {
    lock.synchronized {
      lock.notify()
    }
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