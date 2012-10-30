package play.core.server.servlet25

import javax.servlet._
import javax.servlet.http._
import java.io._
import java.util.Arrays
import java.net.URLDecoder

import play.api._
import play.api.mvc._
import play.api.http._
import play.api.http.HeaderNames._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._
import play.core._
import play.core.server.servlet.{Play2Servlet =>_, _}
import play.core.server.servlet25.Play2Servlet._
import server.Server

import scala.collection.JavaConverters._

object Play2Servlet {

  val DEFAULT_TIMEOUT = 10 * 1000
  
  val syncTimeout = play.core.server.servlet.Play2Servlet.configuration.getInt("servlet25.synctimeout").getOrElse(DEFAULT_TIMEOUT)
  Logger("play").debug("Sync timeout for HTTP requests: " + syncTimeout + " seconds")
}

class Play2Servlet extends play.core.server.servlet.Play2Servlet[Tuple3[HttpServletRequest, HttpServletResponse, Object]] with Helpers {

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