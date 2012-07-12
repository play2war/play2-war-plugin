package play.core.server.servlet30

import javax.servlet._
import javax.servlet.annotation._
import javax.servlet.http._
import java.io._
import java.util.Arrays

import play.api._
import play.api.mvc._
import play.api.http._
import play.api.http.HeaderNames._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._
import play.core._
import play.core.server.servlet._
import server.Server

import scala.collection.JavaConverters._

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends play.core.server.servlet.Play2Servlet[AsyncContext] with Helpers {

  protected override def onBeginService(request: HttpServletRequest): AsyncContext = {
     request.startAsync
  }

  protected override def onFinishService(aSyncContext: AsyncContext) = {
    // Nothing to do
  }
  
  protected override def onHttpResponseComplete(aSyncContext: AsyncContext) = {
	aSyncContext.complete
  }
  
  protected override def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
	Map.empty[String, Seq[String]] ++ request.getParameterMap.asScala.mapValues(Arrays.asList(_: _*).asScala)
  }
  
  protected override def getHttpRequest(aSyncContext: AsyncContext): HttpServletRequest = aSyncContext.getRequest.asInstanceOf[HttpServletRequest]
  
  protected override def getHttpResponse(aSyncContext: AsyncContext): HttpServletResponse = aSyncContext.getResponse.asInstanceOf[HttpServletResponse]

}
