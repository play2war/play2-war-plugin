package play.core.server.servlet31

import javax.servlet.annotation.{WebListener, WebServlet}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import play.api.Logger
import play.core.server.servlet.{GenericPlay2Servlet, Play2WarServer}


object Play2Servlet {
  val asyncTimeout = Play2WarServer.configuration.getInt("servlet31.asynctimeout").getOrElse(-1)
  Logger("play").debug("Async timeout for HTTP requests: " + asyncTimeout + " ms")
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends GenericPlay2Servlet {

  override protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet31RequestHandler(servletRequest)
  }

}

