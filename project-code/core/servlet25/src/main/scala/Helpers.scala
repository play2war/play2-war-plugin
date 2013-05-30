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

import java.util.Arrays
import java.util.concurrent._

import javax.servlet.http.{ Cookie => ServletCookie, _ }

import play.core._
import play.core.server.servlet._
import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._

import scala.collection.JavaConverters._

private[servlet25] trait Helpers extends HTTPHelpers {

  override def getPlayCookie(c: ServletCookie): play.api.mvc.Cookie = play.api.mvc.Cookie(
    c.getName,
    c.getValue,
    Some(c.getMaxAge),
    Option(c.getPath).getOrElse("/"),
    Option(c.getDomain),
    c.getSecure)

  override def getServletCookie(pCookie: play.api.mvc.Cookie): ServletCookie = {
    val sc = new ServletCookie(pCookie.name, pCookie.value)
    pCookie.domain.map(sc.setDomain(_))
    pCookie.maxAge.map(sc.setMaxAge)
    sc.setPath(pCookie.path)
    sc.setSecure(pCookie.secure)
    sc
  }
}
