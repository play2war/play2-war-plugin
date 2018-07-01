package controllers

import java.io._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.stream.scaladsl.{Source, StreamConverters}
import javax.inject.{Inject, Singleton}
import play.api._
import play.api.http.{ContentTypes, HttpEntity}
import play.api.http.HttpEntity.Streamed
import play.api.libs.{Comet, Files}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration._

@Singleton
class Application @Inject()(environment: Environment) extends InjectedController {

  def index = Action {
    Ok(views.html.index("This is a Play 2.0 application, running in a Servlet v3.0 Container :)"))
  }

  def setCookies = Action {
    val result = Ok(views.html.index("Cookies have been set."))

    val cookie1 = new Cookie("cookie1", "value1", Some(3600))
    val cookie2 = new Cookie("cookie2", "value2", Some(3600))

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

  def redirectLanding = Action { implicit request =>
    val flashResult = request.flash.get("success").getOrElse("not found")
    Ok(views.html.redirectLanding(flashResult))
  }

  def redirect = Action {
    Redirect(routes.Application.redirectLanding)
  }

  def internalServerError = Action { request =>
    throw new RuntimeException("This a desired exception in order to test exception interception")
  }

  def httpVersion = Action { request =>
    Ok(request.version)
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
    val dataContent: HttpEntity = Streamed(
      StreamConverters.fromInputStream(() => new ByteArrayInputStream(data)), Some(data.length), None)

    Result(
      header = ResponseHeader(200, Map(CONTENT_LENGTH -> data.length.toString)),
      body = dataContent)
  }

  // Streaming of big content
  def chunkedBigContent = Action { request =>

    val dataContent: Source[String, _] =
      request.queryString.get("maxRange").map {
        maxRange =>
          val iMaxRange = maxRange.head.toInt
          var counter = 0

          Source.unfoldAsync(""){ s =>

            if (counter >= iMaxRange) {
              Future.successful(None)
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

              Future.successful(Some((sb.toString(), s)))
            }
          }
      }.getOrElse {
        Source.single("Max range not found\n")
      }

    Ok.chunked(dataContent)
  }

  def echoGetParameters = Action { request =>
    Ok(views.html.echo(request.queryString))
  }
  
  def echoPostParameters = Action { request =>
    Ok(views.html.echo(request.body.asFormUrlEncoded.get))
  }
  
  def uploadForm = Action { 
    Ok(views.html.uploadForm())
  }

  private def displayUploadDetails(uploadedFile: MultipartFormData.FilePart[Files.TemporaryFile]) = {
    val filename = uploadedFile.filename
    val contentType = uploadedFile.contentType.getOrElse("Unknown")
    val size = uploadedFile.ref.path.toFile.length()
    Ok( s"""File uploaded: $filename
           |Content type: $contentType
           |Size: $size""".stripMargin)
  }

  def upload2 = Action { request =>
    {
      for {
        data <- request.body.asMultipartFormData
        uploadedFile <- data.file("uploadedFile")
      } yield displayUploadDetails(uploadedFile)
    }.getOrElse {
       Ok("Error when uploading")
    }
  }

  def upload = Action(parse.multipartFormData) { request =>
    request.body.file("uploadedFile").map(displayUploadDetails).getOrElse {
      Ok("Error when uploading")
    }
  }

  val df: DateTimeFormatter = DateTimeFormatter.ofPattern("HH mm ss")
  def liveClock() = Action {
    val tickSource = Source.tick(0 millis, 100 millis, "TICK")
    val source = tickSource.map(tick => df.format(ZonedDateTime.now()))
    Ok.chunked(source via Comet.string("parent.clockChanged")).as(ContentTypes.HTML)
  }
  
  def longRequest(duration: Long) = Action {
    Thread.sleep(java.util.concurrent.TimeUnit.SECONDS.toMillis(duration))
    Ok("")
  }

  def flashing = Action {
    Redirect(routes.Application.redirectLanding).flashing(
      "success" -> "found"
    )
  }

  def unmanagedlib = Action {
    val maybeUnmanagedlib = environment.resource("unmanagedlib.txt")

    maybeUnmanagedlib match {
      case Some(_) => Ok("")
      case None => NotFound
    }
  }

}
