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
class Play2Servlet extends play.core.server.servlet.Play2Servlet with Helpers {

  var aSyncContext: AsyncContext = null

  protected override def onBeginService(request: HttpServletRequest) = {
     aSyncContext = request.startAsync
  }

  protected override def onFinishService() = {
    // Nothing to do
  }
  
  protected override def onHttpResponseComplete() = {
	aSyncContext.complete
  }
  
  protected override def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
	Map.empty[String, Seq[String]] ++ request.getParameterMap.asScala.mapValues(Arrays.asList(_: _*).asScala)
  }
  
  protected override def getHttpRequest(): HttpServletRequest = aSyncContext.getRequest.asInstanceOf[HttpServletRequest]
  
  protected override def getHttpResponse(): HttpServletResponse = aSyncContext.getResponse.asInstanceOf[HttpServletResponse]

}
