package controllers

import java.io._

import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def setCookies = Action {
    val result = Ok(views.html.index("Cookies have been set."))

    val cookie1 = new Cookie("cookie1", "value1", 3600)
    val cookie2 = new Cookie("cookie2", "value2", 3600)

    result.withCookies(cookie1).withCookies(cookie2)
  }

  def getCookies = Action { request =>
    var listCookies = List[Cookie]()

    request.cookies.get("cookie1").map { cookie =>
      listCookies ++= Seq(cookie)
    }

    request.cookies.get("cookie2").map { cookie =>
      listCookies ++= Seq(cookie)
    }

    val mapCookies: Map[String, String] = listCookies.map {
      c => (c.name, c.value)
    }.toMap

    println("Cookies sent to view:" + mapCookies)

    Ok(views.html.getCookies(mapCookies))
  }

  def redirectLanding = Action {
    Ok(views.html.redirectLanding())
  }

  def redirect = Action {
    Redirect(routes.Application.redirectLanding)
  }

  def internalServerError = Action { request =>
    throw new RuntimeException("This a desired exception in order to test exception interception")
  }

  // All in memory
  def bigContent = Action { request =>

    val sb = new StringBuilder;

    request.queryString.get("maxRange").map {
      maxRange =>

        for (i <- 0 until maxRange.head.toInt) {
          sb.append(i)
          sb.append("\n")
        }
    }.getOrElse {
      sb.append("Max range not found\n")
    }

    val data = sb.toString.getBytes
    val dataContent: Enumerator[Array[Byte]] = Enumerator.fromStream(new ByteArrayInputStream(data))

    SimpleResult(
      header = ResponseHeader(200, Map(CONTENT_LENGTH -> data.length.toString)),
      body = dataContent)
  }

  // Streaming of big content
  def chunkedBigContent = Action { request =>

    val dataContent: Enumerator[String] =
      request.queryString.get("maxRange").map {
        maxRange =>
          val iMaxRange = maxRange.head.toInt
          var counter = 0

          Enumerator.fromCallback(() => {

            if (counter >= iMaxRange) {
              Promise.pure(None)
            } else {

              val tempCounter = counter + 50000

              import scala.math._
              val minCounter = min(tempCounter, iMaxRange)

              val sb = new StringBuilder

              for (i <- counter until minCounter) {
                sb.append(i)
                sb.append("\n")
              }

              counter = tempCounter

              Promise.pure(Some(sb.toString))
            }
          })
      }.getOrElse {
        Enumerator("Max range not found\n")
      }

    Ok.stream(dataContent >>> Enumerator.eof)
  }

  def echo = Action { request =>
    Ok(views.html.echo(request.queryString))
  }
}