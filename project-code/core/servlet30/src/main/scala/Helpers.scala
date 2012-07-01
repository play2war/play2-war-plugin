package play.core.server.servlet

import java.util.Arrays
import java.util.concurrent._

import javax.servlet.http.{ Cookie => ServletCookie, _ }

import play.core._
import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._

import scala.collection.JavaConverters._

private[servlet] trait Helpers {

  def getPlayHeaders(request: HttpServletRequest): Headers = {

    import java.util.Collections

    val headers: Map[String, Seq[String]] = request.getHeaderNames.asScala.map {
      key =>
        key.toUpperCase ->
          // /!\ It very important to COPY headers from request enumeration
          Collections.list(request.getHeaders(key)).asScala
    }.toMap

    new Headers {

      def getAll(key: String) = headers.get(key.toUpperCase).flatten.toSeq
      def keys = headers.keySet
      override def toString = headers.map {
        case (k, v) => {
          k + ": " + v.mkString(", ")
        }
      }.mkString("\n  ")
    }

  }

  def getPlayCookies(request: HttpServletRequest): Cookies = {

    val cookies: Map[String, play.api.mvc.Cookie] = request.getCookies match {
      case null => Map.empty
      case _ => Arrays.asList(request.getCookies: _*).asScala.map { c =>
        c.getName -> play.api.mvc.Cookie(
          c.getName, c.getValue, c.getMaxAge, Option(c.getPath).getOrElse("/"), Option(c.getDomain), c.getSecure, c.isHttpOnly)
      }.toMap
    }

    new Cookies {
      def get(name: String) = cookies.get(name)
      override def toString = cookies.toString
    }
  }

  def getServletCookies(flatCookie: String): Seq[ServletCookie] = {
    Cookies.decode(flatCookie).map {
      pCookie =>
        val sc = new ServletCookie(pCookie.name, pCookie.value)
        pCookie.domain.map(sc.setDomain(_))
        sc.setHttpOnly(pCookie.httpOnly)
        sc.setMaxAge(pCookie.maxAge)
        sc.setPath(pCookie.path)
        sc.setSecure(pCookie.secure)
        sc
    }
  }
}
