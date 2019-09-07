package play.core.server.servlet31

import javax.servlet.annotation.{WebListener, WebServlet}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import play.api.Logger
import play.core.server.servlet.{GenericPlay2Servlet, Play2WarServer}


object Play2Servlet {
  private[this] val configuration = Play2WarServer.configuration
  val asyncTimeout: Int = configuration.getOptional[Int]("servlet31.asynctimeout").getOrElse(-1)
  val internalUploadBufferSide: Int = configuration.getOptional[Int]("servlet31.upload.internalbuffersize").getOrElse(1024 * 8)
  Logger("play").debug("Async timeout for HTTP requests: " + asyncTimeout + " ms")
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends GenericPlay2Servlet {

  override protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet31RequestHandler(servletRequest)
  }

}

