package play.core.server.servlet

import java.util.Arrays
import java.util.Collections

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

import javax.servlet.http.{Cookie => ServletCookie}
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.mvc.Cookies
import play.api.mvc.Headers

trait HTTPHelpers {

  def getPlayHeaders(request: HttpServletRequest): Headers = {

    import java.util.Collections

      val headerNames = request.getHeaderNames.asScala
      
      val allHeaders: Map[String, Seq[String]] = headerNames.map {
        key =>
          key.toString.toUpperCase -> {
            // /!\ It very important to COPY headers from request enumeration
            val headers = Collections.list(request.getHeaders(key.toString)).asScala
            headers.asInstanceOf[Seq[String]]
          }
      }.toMap

    new Headers {

      protected def data: Seq[(String, Seq[String])] = {
        allHeaders.toSeq
      }
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
  
  def getRichInputStream: Option[java.io.InputStream]
}

trait RichHttpServletResponse {
  
  def getRichOutputStream: Option[java.io.OutputStream]
  
  def getHttpServletResponse: Option[HttpServletResponse]
}
