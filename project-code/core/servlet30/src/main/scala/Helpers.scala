package play.core.server.servlet30

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

private[servlet30] trait Helpers extends HTTPHelpers {

  override def getPlayCookie(c: ServletCookie) = play.api.mvc.Cookie(
    c.getName,
    c.getValue,
    c.getMaxAge,
    Option(c.getPath).getOrElse("/"),
    Option(c.getDomain),
    c.getSecure,
    c.isHttpOnly)

  override def getServletCookie(pCookie: play.api.mvc.Cookie) = {
    val sc = new ServletCookie(pCookie.name, pCookie.value)
    pCookie.domain.map(sc.setDomain(_))
    sc.setHttpOnly(pCookie.httpOnly)
    sc.setMaxAge(pCookie.maxAge)
    sc.setPath(pCookie.path)
    sc.setSecure(pCookie.secure)
    sc
  }
}
