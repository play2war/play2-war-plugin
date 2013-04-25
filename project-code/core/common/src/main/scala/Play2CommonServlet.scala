package play.core.server.servlet

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Mother class for all servlet implementations for Play2.
 */
abstract class GenericPlay2Servlet extends HttpServlet with ServletContextListener {

  /**
   * Classic "service" servlet method.
   */
  override protected def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {

    val requestHandler = getRequestHandler(servletRequest, servletResponse)

    Play2WarServer.handleRequest(requestHandler)
  }

  protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse): RequestHandler

  override def contextInitialized(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    // Init or get singleton
    Play2WarServer(Some(e.getServletContext.getContextPath))
  }

  override def contextDestroyed(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextDestroyed")

    Play2WarServer.stop(e.getServletContext)
  }

  override def destroy = {
    getServletContext.log("PlayServletWrapper > destroy")

    Play2WarServer.stop(getServletContext)
  }

}
