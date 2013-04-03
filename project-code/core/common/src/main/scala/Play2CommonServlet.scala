package play.core.server.servlet

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.net.URLDecoder
import java.util.logging.Handler

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.{ Cookie => ServletCookie }
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.concurrent.Promise
import play.api.libs.concurrent.Redeemed
import play.api.libs.concurrent.Thrown
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Action
import play.api.mvc.AsyncResult
import play.api.mvc.ChunkedResult
import play.api.mvc.Cookies
import play.api.mvc.Headers
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Response
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.SimpleResult
import play.api.mvc.WebSocket

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.{ Cookie => ServletCookie }
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.concurrent.Promise
import play.api.libs.concurrent.Redeemed
import play.api.libs.concurrent.Thrown
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Action
import play.api.mvc.AsyncResult
import play.api.mvc.ChunkedResult
import play.api.mvc.Cookies
import play.api.mvc.Headers
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Response
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.SimpleResult
import play.api.mvc.WebSocket

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
