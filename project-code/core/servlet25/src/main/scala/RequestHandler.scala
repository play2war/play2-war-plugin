package play.core.server.servlet25

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.core.server.servlet.Play2GenericServletRequestHandler
import play.core.server.servlet.RichHttpServletRequest
import play.core.server.servlet.RichHttpServletResponse

class Play2Servlet25RequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse)
  extends Play2GenericServletRequestHandler[Tuple3[HttpServletRequest, HttpServletResponse, Object]](servletRequest, servletResponse)
  with Helpers {

  protected override def onBeginService(request: HttpServletRequest, response: HttpServletResponse): Tuple3[HttpServletRequest, HttpServletResponse, Object] = {
    (request, response, new Object())
  }

  protected override def onFinishService(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      execContext._3.wait(Play2Servlet.syncTimeout)
    }
  }

  protected override def onHttpResponseComplete(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      execContext._3.notify()
    }
  }

  protected override def getHttpRequest(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[java.io.InputStream] = {
        Option(executionContext._1.getInputStream)
      }
    }
  }

  protected override def getHttpResponse(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[java.io.OutputStream] = {
        Option(executionContext._2.getOutputStream)
      }

      def getHttpServletResponse: Option[HttpServletResponse] = {
        Option(executionContext._2)
      }
    }
  }

}