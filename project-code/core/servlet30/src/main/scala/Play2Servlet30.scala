package play.core.server.servlet30

import javax.servlet.annotation.WebListener
import javax.servlet.annotation.WebServlet
import play.api.Logger
import play.core.server.servlet.Play2WarServer
import play.core.server.servlet.GenericPlay2Servlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object Play2Servlet {
  val asyncTimeout = Play2WarServer.configuration.getInt("servlet30.asynctimeout").getOrElse(-1)
  Logger("play").debug("Async timeout for HTTP requests: " + asyncTimeout + " seconds")
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends GenericPlay2Servlet {

  override protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet30RequestHandler(servletRequest)
  }

}
