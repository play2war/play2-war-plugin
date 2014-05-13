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

import javax.servlet.http.{ Cookie => ServletCookie }

import play.core.server.servlet._


private[servlet30] trait Helpers extends HTTPHelpers {

  override def getPlayCookie(c: ServletCookie): play.api.mvc.Cookie = play.api.mvc.Cookie(
    name = c.getName,
    value = c.getValue,
    maxAge = if (c.getMaxAge == -1) None else Some(c.getMaxAge),
    path = Option(c.getPath).getOrElse("/"),
    domain = Option(c.getDomain),
    secure = c.getSecure,
    httpOnly = c.isHttpOnly)

  override def getServletCookie(pCookie: play.api.mvc.Cookie): ServletCookie = {
    val sc = new ServletCookie(pCookie.name, pCookie.value)
    pCookie.domain.map(sc.setDomain)
    sc.setHttpOnly(pCookie.httpOnly)

    // conversion Play cookie to servlet cookie
    // |---------------------------------------|------------------|--------------------------|
    // | Use case                              | Play             | Servlet Container        |
    // |---------------------------------------|------------------|--------------------------|
    // | tell the browser to delete the cookie | Some(maxAge < 0) | maxAge = 0               |
    // | set a session cookie                  | None             | maxAge = -1 (or not set) |
    // | set a persistent cookie               | Some(maxAge > 0) | maxAge > 0               |
    // |---------------------------------------|------------------|--------------------------|
    pCookie.maxAge.map(ex => if (ex < 0) sc.setMaxAge(0) else sc.setMaxAge(ex))

    sc.setPath(pCookie.path)
    sc.setSecure(pCookie.secure)
    sc
  }
}
