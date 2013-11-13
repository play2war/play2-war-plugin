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
package play.core.server.servlet

import java.io.ByteArrayOutputStream
import java.net.{URI, URLDecoder}
import java.util.concurrent.atomic.AtomicBoolean

import scala.io._

import javax.servlet.http.{ Cookie => ServletCookie }
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api._
import play.api.Logger
import play.api.http.HeaderNames
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import scala.concurrent.Future
import scala.util.control.Exception

trait RequestHandler {

  def apply(server: Play2WarServer)

}

trait HttpServletRequestHandler extends RequestHandler {

  protected def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]]

  protected def getPlayHeaders(request: HttpServletRequest): Headers

  protected def getPlayCookies(request: HttpServletRequest): Cookies

  /**
   * Get a list of cookies from "flat" cookie representation (one-line-string cookie).
   */
  protected def getServletCookies(flatCookie: String): Seq[ServletCookie]

  /**
   * Get HTTP request.
   */
  protected def getHttpRequest(): RichHttpServletRequest

  /**
   * Get HTTP response.
   */
  protected def getHttpResponse(): RichHttpServletResponse

  /**
   * Call just before end of service(...).
   */
  protected def onFinishService(): Unit

  /**
   * Call every time the HTTP response must be terminated (completed).
   */
  protected def onHttpResponseComplete(): Unit

}

/**
 * Generic implementation of HttpServletRequestHandler.
 * One instance per incoming HTTP request.
 *
 * <strong>/!\ Warning: this class and its subclasses are intended to thread-safe.</strong>
 */
abstract class Play2GenericServletRequestHandler(val servletRequest: HttpServletRequest, val servletResponse: Option[HttpServletResponse]) extends HttpServletRequestHandler {

  override def apply(server: Play2WarServer) = {

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val httpVersion = servletRequest.getProtocol
    val servletPath = servletRequest.getRequestURI
    val servletUri = servletPath + Option(servletRequest.getQueryString).filterNot(_.isEmpty).map { "?" + _ }.getOrElse { "" }
    val parameters = getHttpParameters(servletRequest)
    val rHeaders = getPlayHeaders(servletRequest)
    val httpMethod = servletRequest.getMethod

    def rRemoteAddress = {
      val remoteAddress = servletRequest.getRemoteAddr
      (for {
        xff <- rHeaders.get(X_FORWARDED_FOR)
        app <- server.applicationProvider.get.toOption
        trustxforwarded <- app.configuration.getBoolean("trustxforwarded").orElse(Some(false))
        if remoteAddress == "127.0.0.1" || trustxforwarded
      } yield xff).getOrElse(remoteAddress)
    }

    def tryToCreateRequest = createRequestHeader(parameters)

    def createRequestHeader(parameters: Map[String, Seq[String]] = Map.empty[String, Seq[String]]) = {
      //mapping servlet request to Play's
      val untaggedRequestHeader = new RequestHeader {
        val id = server.newRequestId
        val tags = Map.empty[String,String]
        def uri = servletUri
        def path = servletPath
        def method = httpMethod
        def version = httpVersion
        def queryString = parameters
        def headers = rHeaders
        lazy val remoteAddress = rRemoteAddress
      }
      untaggedRequestHeader
    }

    // get handler for request
    val (requestHeader, handler: Either[Future[SimpleResult],(Handler,Application)]) = Exception
      .allCatch[RequestHeader].either(tryToCreateRequest)
      .fold(
      e => {
        val rh = createRequestHeader()
        val r = server.applicationProvider.get.map(_.global).getOrElse(DefaultGlobal).onBadRequest(rh, e.getMessage)
        (rh, Left(r))
      },
      rh => server.getHandlerFor(rh) match {
        case directResult @ Left(_) => (rh, directResult)
        case Right((taggedRequestHeader, handler, application)) => (taggedRequestHeader, Right((handler, application)))
      }
    )

    // Call onRequestCompletion after all request processing is done. Protected with an AtomicBoolean to ensure can't be executed more than once.
    val alreadyClean = new java.util.concurrent.atomic.AtomicBoolean(false)
    def cleanup() {
      if (!alreadyClean.getAndSet(true)) {
        play.api.Play.maybeApplication.foreach(_.global.onRequestCompletion(requestHeader))
      }
    }

    trait Response {
      def handle(result: SimpleResult): Unit
    }

    // converting servlet response to play's
    val response = new Response {

      def handle(result: SimpleResult): Unit = {

        getHttpResponse().getHttpServletResponse.foreach { httpResponse =>

          val status = result.header.status
          val headers = result.header.headers
          val body = result.body

          // TODO: handle connection KeepAlive and Close?
          val connection = result.connection


          import play.api.libs.iteratee.Execution.Implicits.trampoline

          Logger("play").trace("Sending simple result: " + result)

          httpResponse.setStatus(status)

          // Set response headers
          headers.filterNot(_ == (CONTENT_LENGTH, "-1")).foreach {

            // Fix a bug for Set-Cookie header.
            // Multiple cookies could be merged in a single header
            // but it's not properly supported by some browsers
            case (name @ play.api.http.HeaderNames.SET_COOKIE, value) => {
              getServletCookies(value).map {
                c => httpResponse.addCookie(c)
              }
            }

            case (name, value) => httpResponse.setHeader(name, value)
          }

          // Stream the result
          headers.get(CONTENT_LENGTH).map { contentLength =>
            Logger("play").trace("Result with Content-length: " + contentLength)

            var hasError: AtomicBoolean = new AtomicBoolean(false)

            val bodyIteratee: Iteratee[Array[Byte], Unit] = {

              def step(in: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = (!hasError.get, in) match {
                case (true, Input.El(x)) =>
                  Iteratee.flatten(
                    Promise.pure(
                      if (hasError.get) {
                        ()
                      } else {
                        getHttpResponse().getRichOutputStream.foreach { os =>
                          os.write(x)
                          os.flush()
                        }
                      })
                      //.map(_ => if (!hasError.get) Cont(step) else Done((), Input.Empty)))
                      .extend1 {
                        case Redeemed(_) => if (!hasError.get) Cont(step) else Done((), Input.Empty)
                        case Thrown(ex) =>
                          hasError.set(true)
                          Logger("play").debug(ex.toString)
                          throw ex
                      })
                case (true, Input.Empty) => Cont(step)
                case (_, inp) => Done((), inp)
              }
              Iteratee.flatten(
                Promise.pure(())
                  .map(_ => if (!hasError.get) Cont(step) else Done((), Input.Empty: Input[Array[Byte]])))
            }

            (body |>>> bodyIteratee).extend1 {
              case Redeemed(_) =>
                cleanup()
                onHttpResponseComplete
              case Thrown(ex) =>
                Logger("play").debug(ex.toString)
                hasError.set(true)
                onHttpResponseComplete
            }
          }.getOrElse {
            Logger("play").trace("Result without Content-length")

            // No Content-Length header specified, buffer in-memory
            val byteBuffer = new ByteArrayOutputStream
            val writer: Function2[ByteArrayOutputStream, Array[Byte], Unit] = (b, x) => b.write(x)
            val stringIteratee = Iteratee.fold(byteBuffer)((b, e: Array[Byte]) => { writer(b, e); b })

            val p = (body |>>> Enumeratee.grouped(stringIteratee) &>> Cont {
              case Input.El(buffer) =>
                Logger("play").trace("Buffer size to send: " + buffer.size)
                getHttpResponse().getRichOutputStream.map { os =>
                  getHttpResponse().getHttpServletResponse.map(_.setContentLength(buffer.size))
                  os.flush
                  buffer.writeTo(os)
                }
                val p = Promise.pure()
                Iteratee.flatten(p.map(_ => Done(1, Input.Empty: Input[ByteArrayOutputStream])))

              case other => Error("unexpected input", other)
            })
            p.extend1 {
              case Redeemed(_) =>
                cleanup()
                onHttpResponseComplete
              case Thrown(ex) =>
                Logger("play").debug(ex.toString)
                onHttpResponseComplete
            }
          }

        } // end match foreach

      } // end handle method

    }

    def cleanFlashCookie(result: SimpleResult): SimpleResult = {
      val header = result.header

      val flashCookie = {
        header.headers.get(HeaderNames.SET_COOKIE)
          .map(Cookies.decode(_))
          .flatMap(_.find(_.name == Flash.COOKIE_NAME)).orElse {
            Option(requestHeader.flash).filterNot(_.isEmpty).map { _ =>
              Flash.discard.toCookie
            }
          }
      }

      flashCookie.map { newCookie =>
        result.withHeaders(HeaderNames.SET_COOKIE -> Cookies.merge(header.headers.get(HeaderNames.SET_COOKIE).getOrElse(""), Seq(newCookie)))
      }.getOrElse(result)
    }

    handler match {

      //execute normal action
      case Right((action: EssentialAction, app)) =>
        val a = EssentialAction { rh =>
          import play.api.libs.iteratee.Execution.Implicits.trampoline
          Iteratee.flatten(action(rh).unflatten.map(_.it).recover {
            case error =>
              Iteratee.flatten(
                app.handleError(requestHeader, error).map(result => Done(result, Input.Empty))
              ): Iteratee[Array[Byte],SimpleResult]
          })
        }
        handleAction(a, Some(app))

      //handle all websocket request as bad, since websocket are not handled
      //handle bad websocket request
      case Right((WebSocket(_), app)) =>
        Logger("play").trace("Bad websocket request")
        val a = EssentialAction(_ => Done(Results.BadRequest, Input.Empty))
        handleAction(a, Some(app))

      case Left(e) =>
        Logger("play").trace("No handler, got direct result: " + e)
        import play.api.libs.iteratee.Execution.Implicits.trampoline
        val a = EssentialAction(_ => Iteratee.flatten(e.map(result => Done(result, Input.Empty))))
        handleAction(a,None)
    }

    def handleAction(action: EssentialAction, app: Option[Application]) {
      Logger("play").trace("Serving this request with: " + action)

      val bodyParser = Iteratee.flatten(
        scala.concurrent.Future(action(requestHeader))(play.api.libs.concurrent.Execution.defaultContext)
      )

      import play.api.libs.iteratee.Execution.Implicits.trampoline

      // Remove Except: 100-continue handling, since it's impossible to handle it
      //val expectContinue: Option[_] = requestHeader.headers.get("Expect").filter(_.equalsIgnoreCase("100-continue"))

      val bodyEnumerator = getHttpRequest().getRichInputStream.map { is =>
        val output = new java.io.ByteArrayOutputStream()
        val buffer = new Array[Byte](1024 * 8)
        var length = is.read(buffer)
        while(length != -1){
          output.write(buffer, 0, length)
          length = is.read(buffer)
        }
        Enumerator(output.toByteArray) andThen Enumerator.eof
      }.getOrElse(Enumerator.eof)


      val eventuallyResult: Future[SimpleResult] = bodyEnumerator |>>> bodyParser

      val sent = eventuallyResult.recoverWith {
        case error =>
          Logger("play").error("Cannot invoke the action, eventually got an error: " + error)
          app.map(_.handleError(requestHeader, error))
            .getOrElse(DefaultGlobal.onError(requestHeader, error))
      }.map { result =>
        response.handle(cleanFlashCookie(result))
      }

      // Finally, clean up
      sent.map { _ =>
        cleanup()
      }
    }

    onFinishService()

  }

  override protected def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
    request.getQueryString match {
      case null | "" => Map.empty
      case queryString => queryString.replaceFirst("^?", "").split("&").map { queryElement =>
        val array = queryElement.split("=")
        array.length match {
          case 0 => None
          case 1 => Some(URLDecoder.decode(array(0), "UTF-8") -> "")
          case _ => Some(URLDecoder.decode(array(0), "UTF-8") -> URLDecoder.decode(array(1), "UTF-8"))
        }
      }.flatten.groupBy(_._1).map { case (key, value) => key -> value.map(_._2).toSeq }.toMap
    }
  }

}
