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

trait HTTPHelpers {

  def getPlayHeaders(request: HttpServletRequest): Headers = {

    import java.util.Collections

      val headerNames = request.getHeaderNames.asScala
      
      val allHeaders: Map[String, Seq[String]] = headerNames.map {
        key =>
          key.toString.toUpperCase -> {
            // /!\ It very important to COPY headers from request enumeration
            val headers = Collections.list(request.getHeaders(key.toString)).asScala
            headers.map { t => t.toString }
          }
      }.toMap

    new Headers {

      def getAll(key: String) = allHeaders.get(key.toUpperCase).flatten.toSeq
      def keys = allHeaders.keySet
      override def toString = allHeaders.map {
        case (k, v) => {
          k + ": " + v.mkString(", ")
        }
      }.mkString("\n  ")
    }
  }

  final def getPlayCookies(request: HttpServletRequest): Cookies = {

    val cookies: Map[String, play.api.mvc.Cookie] = request.getCookies match {
      case null => Map.empty
      case _ => Arrays.asList(request.getCookies: _*).asScala.map { c =>
        c.getName -> getPlayCookie(c)
      }.toMap
    }

    new Cookies {
      def get(name: String) = cookies.get(name)
      override def toString = cookies.toString
    }
  }
  
  def getPlayCookie(c: ServletCookie): play.api.mvc.Cookie

  final def getServletCookies(flatCookie: String): Seq[ServletCookie] = {
    Cookies.decode(flatCookie).map {
      pCookie => getServletCookie(pCookie)
    }
  }
  
  def getServletCookie(pCookie: play.api.mvc.Cookie): ServletCookie
}

trait RichHttpServletRequest {
  
  def getRichInputStream(): Option[java.io.InputStream] 
}

trait RichHttpServletResponse {
  
  def getRichOutputStream(): Option[java.io.OutputStream] 
}
