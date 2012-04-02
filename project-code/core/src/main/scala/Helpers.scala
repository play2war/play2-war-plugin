package play.core.server.servlet

import java.util.Arrays
import java.util.concurrent._

import javax.servlet.http._

import play.core._
import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._

import scala.collection.JavaConverters._

private[servlet] trait Helpers {

  def getPlayHeaders(request: HttpServletRequest): Headers = {

    val headers = request.getHeaderNames.asScala.map {
      key =>
        key.toUpperCase ->
          request.getHeaders(key).asScala
    }.toMap

    new Headers {
      def getAll(key: String) = headers.get(key.toUpperCase).flatten.toSeq
      def keys = headers.keySet
      override def toString = headers.toString
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
}
