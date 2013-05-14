package play.core.server.servlet25

import play.api.Logger
import play.core.server.servlet.GenericPlay2Servlet
import play.core.server.servlet.Play2WarServer
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object Play2Servlet {

  val DEFAULT_TIMEOUT = 60 * 1000

  val syncTimeout = Play2WarServer.configuration.getInt("servlet25.synctimeout").getOrElse(DEFAULT_TIMEOUT)
  Logger("play").debug("Sync timeout for HTTP requests: " + syncTimeout + " seconds")
}

class Play2Servlet extends GenericPlay2Servlet with Helpers {

  override protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet25RequestHandler(servletRequest, servletResponse)
  }

}