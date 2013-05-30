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
package play.core.server.servlet25

import play.api.Logger
import play.core.server.servlet.GenericPlay2Servlet
import play.core.server.servlet.Play2WarServer
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object Play2Servlet {

  val DEFAULT_TIMEOUT = 60 * 1000

  val syncTimeout = Play2WarServer.configuration.getInt("servlet25.synctimeout").getOrElse(DEFAULT_TIMEOUT)
  Logger("play").debug("Sync timeout for HTTP requests: " + syncTimeout + " ms")
}

class Play2Servlet extends GenericPlay2Servlet with Helpers {

  override protected def getRequestHandler(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    new Play2Servlet25RequestHandler(servletRequest, servletResponse)
  }

}
