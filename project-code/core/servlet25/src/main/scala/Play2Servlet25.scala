package play.core.server.servlet25

import javax.servlet._
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
import play.core.server.servlet25.Play2Servlet._
import server.Server

import scala.collection.JavaConverters._

object Play2Servlet {

  // 30 minutes in milliseconds
  // val WAIT_TIMEOUT = 30 * 60 * 1000
  val WAIT_TIMEOUT = 10 * 1000
}

class Play2Servlet extends play.core.server.servlet.Play2Servlet[Tuple3[HttpServletRequest, HttpServletResponse, Object]] with Helpers {

  protected override def onBeginService(request: HttpServletRequest, response: HttpServletResponse): Tuple3[HttpServletRequest, HttpServletResponse, Object] = {
     (request, response, new Object())
  }

  protected override def onFinishService(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      execContext._3.wait(WAIT_TIMEOUT)
    }
  }
  
  protected override def onHttpResponseComplete(execContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]) = {
    execContext._3.synchronized {
      execContext._3.notify()
    }
  }
  
  protected override def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
    val parameterMap = request.getParameterMap.asInstanceOf[java.util.Map[String, Array[String]]].asScala
	Map.empty[String, Seq[String]] ++ parameterMap.mapValues(Arrays.asList(_: _*).asScala)
  }
  
  protected override def getHttpRequest(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): HttpServletRequest = executionContext._1
  
  protected override def getHttpResponse(executionContext: Tuple3[HttpServletRequest, HttpServletResponse, Object]): HttpServletResponse = executionContext._2

}