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

  override def contextInitialized(e: ServletContextEvent): Unit = {
     println("PlayServletWrapper > contextInitialized")

    // Init or get singleton
    Play2WarServer(Some(e.getServletContext.getContextPath))
  }

  override def contextDestroyed(e: ServletContextEvent): Unit = {
    println("PlayServletWrapper > contextDestroyed")

    Play2WarServer.stop(e.getServletContext)
  }

  override def destroy(): Unit = {
    println("PlayServletWrapper > destroy")

    Play2WarServer.stop(getServletContext)
  }

}
