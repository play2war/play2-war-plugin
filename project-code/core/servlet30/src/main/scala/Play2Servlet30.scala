/*
 * Copyright 2013 Damien Lecan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.core.server.servlet30

import javax.servlet.annotation.WebListener
import javax.servlet.annotation.WebServlet
import play.api.Logger
import play.core.server.servlet.Play2WarServer
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object Play2Servlet {
  val asyncTimeout = Play2WarServer.configuration.getInt("servlet30.asynctimeout").getOrElse(-1)
  Logger("play").debug("Async timeout for HTTP requests: " + asyncTimeout + " ms")
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends HttpServlet with ServletContextListener {


/**
   * Classic "service" servlet method.
   */
  override protected def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {

    val requestHandler = getRequestHandler(servletRequest, servletResponse)

    Play2WarServer.handleRequest(requestHandler)
  }

  protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet30RequestHandler(servletRequest)
  }

  override def contextInitialized(e: ServletContextEvent): Unit = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    // Init or get singleton
    Play2WarServer(Some(e.getServletContext.getContextPath))
  }

  override def contextDestroyed(e: ServletContextEvent): Unit = {
    e.getServletContext.log("PlayServletWrapper > contextDestroyed")

    Play2WarServer.stop(e.getServletContext)
  }

  override def destroy(): Unit = {
    getServletContext.log("PlayServletWrapper > destroy")

    Play2WarServer.stop(getServletContext)
  }

  


}
