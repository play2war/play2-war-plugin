package play.core.server.servlet

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.net.URLDecoder
import java.util.logging.Handler

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.{Cookie => ServletCookie}
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.concurrent.Promise
import play.api.libs.concurrent.Redeemed
import play.api.libs.concurrent.Thrown
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Action
import play.api.mvc.AsyncResult
import play.api.mvc.ChunkedResult
import play.api.mvc.Cookies
import play.api.mvc.Headers
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Response
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.SimpleResult
import play.api.mvc.WebSocket

import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.http.{Cookie => ServletCookie}
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api.Logger
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.concurrent.Promise
import play.api.libs.concurrent.Redeemed
import play.api.libs.concurrent.Thrown
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Action
import play.api.mvc.AsyncResult
import play.api.mvc.ChunkedResult
import play.api.mvc.Cookies
import play.api.mvc.Headers
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Response
import play.api.mvc.ResponseHeader
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.SimpleResult
import play.api.mvc.WebSocket

/**
 * Mother class for all servlet implementations for Play2.
 */
abstract class Play2Servlet[T] extends HttpServlet with ServletContextListener {

  protected def getPlayHeaders(request: HttpServletRequest): Headers

  protected def getPlayCookies(request: HttpServletRequest): Cookies

  /**
   * Get a list of cookies from "flat" cookie representation (one-line-string cookie).
   */
  protected def getServletCookies(flatCookie: String): Seq[ServletCookie]

  /**
   * Get HTTP request.
   */
  protected def getHttpRequest(execContext: T): RichHttpServletRequest

  /**
   * Get HTTP response.
   */
  protected def getHttpResponse(execContext: T): RichHttpServletResponse

  /**
   * Call just after service(...).
   */
  protected def onBeginService(request: HttpServletRequest, response: HttpServletResponse): T

  /**
   * Call just before end of service(...).
   */
  protected def onFinishService(execContext: T): Unit

  /**
   * Call every time the HTTP response must be terminated (completed).
   */
  protected def onHttpResponseComplete(execContext: T): Unit

  /**
   * Classic "service" servlet method.
   */
  protected override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    Logger("play").trace("HTTP request received: " + servletRequest)

    val execContext: T = onBeginService(servletRequest, servletResponse)

    val server = Play2WarServer.playServer

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val version = servletRequest.getProtocol.substring("HTTP/".length, servletRequest.getProtocol.length)
    val servletPath = servletRequest.getRequestURI
    val servletUri = servletPath + Option(servletRequest.getQueryString).filterNot(_.isEmpty).map { "?" + _ }.getOrElse { "" }
    val parameters = getHttpParameters(servletRequest)
    val rHeaders = getPlayHeaders(servletRequest)
    val rCookies = getPlayCookies(servletRequest)
    val httpMethod = servletRequest.getMethod

    def rRemoteAddress = {
      val remoteAddress = servletRequest.getRemoteAddr
      (for {
        xff <- rHeaders.get(X_FORWARDED_FOR)
        app <- server.applicationProvider.get.right.toOption
        trustxforwarded <- app.configuration.getBoolean("trustxforwarded").orElse(Some(false))
        if remoteAddress == "127.0.0.1" || trustxforwarded
      } yield xff).getOrElse(remoteAddress)
    }

    val requestHeader = new RequestHeader {
      def uri = servletUri
      def path = servletPath
      def method = httpMethod
      def queryString = parameters
      def headers = rHeaders
      lazy val remoteAddress = rRemoteAddress
      def username = None

      override def toString = {
        super.toString + "\nURI: " + uri + "\nMethod: " + method + "\nPath: " + path + "\nParameters: " + queryString + "\nHeaders: " + headers + "\nCookies: " + rCookies
      }
    }
    Logger("play").trace("HTTP request content: " + requestHeader)

    // converting servlet response to play's
    val response = new Response {

      def handle(result: Result) {

        getHttpResponse(execContext).getHttpServletResponse.foreach { httpResponse =>

          result match {

            case AsyncResult(p) => p.extend1 {
              case Redeemed(v) => handle(v)
              case Thrown(e) => {
                Logger("play").error("Waiting for a promise, but got an error: " + e.getMessage, e)
                handle(Results.InternalServerError)
              }
            }

            case r @ SimpleResult(ResponseHeader(status, headers), body) => {
              Logger("play").trace("Sending simple result: " + r)

              httpResponse.setStatus(status)

              // Set response headers
              headers.filterNot(_ == (CONTENT_LENGTH, "-1")).foreach {

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

                val writer: Function1[r.BODY_CONTENT, Promise[Unit]] = x => {
                  Promise.pure(
                    {
                      if (hasError.get) {
                        ()
                      } else {
                        getHttpResponse(execContext).getRichOutputStream.foreach { os =>
                          os.write(r.writeable.transform(x))
                          os.flush
                        }
                      }
                    }).extend1 {
                      case Redeemed(()) => ()
                      case Thrown(ex) => {
                        hasError.set(true)
                        Logger("play").debug("Exception received while writing to client: " + ex.toString)
                      }
                    }
                }

                val bodyIteratee = {
                  val writeIteratee = Iteratee.fold1(
                    Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))

                  Enumeratee.breakE[r.BODY_CONTENT](_ => hasError.get)(writeIteratee).mapDone { _ =>
                    onHttpResponseComplete(execContext)
                  }
                }

                body(bodyIteratee)
              }.getOrElse {
                Logger("play").trace("Result without Content-length")

                // No Content-Length header specified, buffer in-memory
                val byteBuffer = new ByteArrayOutputStream
                val writer: Function2[ByteArrayOutputStream, r.BODY_CONTENT, Unit] = (b, x) => b.write(r.writeable.transform(x))
                val stringIteratee = Iteratee.fold(byteBuffer)((b, e: r.BODY_CONTENT) => { writer(b, e); b })
                val p = body |>> stringIteratee

                p.flatMap(i => i.run)
                  .onRedeem { buffer =>
                    Logger("play").trace("Buffer size to send: " + buffer.size)
                    getHttpResponse(execContext).getRichOutputStream.map { os =>
                      getHttpResponse(execContext).getHttpServletResponse.map(_.setContentLength(buffer.size))
                      os.flush
                      buffer.writeTo(os)
                    }
                    onHttpResponseComplete(execContext)
                  }
              }
            }

            case r @ ChunkedResult(ResponseHeader(status, headers), chunks) => {
              Logger("play").trace("Sending chunked result: " + r)

              httpResponse.setStatus(status)

              // Copy headers to netty response
              headers.foreach {

                case (name @ play.api.http.HeaderNames.SET_COOKIE, value) => {
                  getServletCookies(value).map {
                    c => httpResponse.addCookie(c)
                  }
                }

                case (name, value) => httpResponse.setHeader(name, value)
              }

              var hasError: AtomicBoolean = new AtomicBoolean(false)

              val writer: Function1[r.BODY_CONTENT, Promise[Unit]] = x => {
                Promise.pure(
                  {
                    if (hasError.get) {
                      ()
                    } else {
                      getHttpResponse(execContext).getRichOutputStream.foreach { os =>
                        os.write(r.writeable.transform(x))
                        os.flush
                      }
                    }
                  }).extend1 {
                    case Redeemed(()) => ()
                    case Thrown(ex) => {
                      hasError.set(true)
                      Logger("play").debug("Exception received while writing to client: " + ex.toString)
                    }
                  }
              }

              val chunksIteratee = {
                val writeIteratee = Iteratee.fold1(
                  Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))

                Enumeratee.breakE[r.BODY_CONTENT](_ => hasError.get)(writeIteratee).mapDone { _ =>
                  onHttpResponseComplete(execContext)
                }
              }

              chunks(chunksIteratee)
            }

            case defaultResponse @ _ =>
              Logger("play").trace("Default response: " + defaultResponse)
              Logger("play").error("Unhandle default response: " + defaultResponse)

              httpResponse.setContentLength(0);
              httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
              onHttpResponseComplete(execContext)

          } // end match result

        } // end match foreach

      } // end handle method

    }

    // get handler for request
    val handler = server.getHandlerFor(requestHeader)

    handler match {

      //execute normal action
      case Right((action: Action[_], app)) => {

        Logger("play").trace("Serving this request with: " + action)

        val bodyParser = action.parser

        val eventuallyBodyParser = server.getBodyParser[action.BODY_CONTENT](requestHeader, bodyParser)

        val _ =
          eventuallyBodyParser.flatMap { bodyParser =>

            requestHeader.headers.get("Expect") match {
              case Some("100-continue") => {
                bodyParser.pureFold(
                  (_, _) => (),
                  k => {
                    //                        val continue = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
                    //                        e.getChannel.write(continue)
                    ()
                  },
                  (_, _) => ())
              }

              case _ => Promise.pure()
            }
          }

        lazy val bodyEnumerator = getHttpRequest(execContext).getRichInputStream.map { is =>
          Enumerator.fromStream(is).andThen(Enumerator.eof)
        }.getOrElse(Enumerator.eof)

        val eventuallyResultOrBody = eventuallyBodyParser.flatMap(it =>
          bodyEnumerator |>> it): Promise[Iteratee[Array[Byte], Either[Result, action.BODY_CONTENT]]]

        val eventuallyResultOrRequest =
          eventuallyResultOrBody
            .flatMap(it => it.run)
            .map {
              _.right.map(b =>
                new Request[action.BODY_CONTENT] {
                  def uri = servletUri
                  def path = servletPath
                  def method = httpMethod
                  def queryString = parameters
                  def headers = rHeaders
                  lazy val remoteAddress = rRemoteAddress
                  def username = None
                  val body = b
                })
            }

        eventuallyResultOrRequest.extend(_.value match {
          case Redeemed(Left(result)) => {
            Logger("play").trace("Got direct result from the BodyParser: " + result)
            response.handle(result)
          }
          case Redeemed(Right(request)) => {
            Logger("play").trace("Invoking action with request: " + request)
            server.invoke(request, response, action.asInstanceOf[Action[action.BODY_CONTENT]], app)
          }
          case error => {
            Logger("play").error("Cannot invoke the action, eventually got an error: " + error)
            response.handle(Results.InternalServerError)
          }
        })

      }

      //handle websocket action
      case Right((ws @ WebSocket(f), app)) => {
        Logger("play").error("Impossible to serve Web Socket request:" + ws)
        response.handle(Results.InternalServerError)
      }

      case unexpected => {
        Logger("play").error("Oops, unexpected message received in Play server (please report this problem): " + unexpected)
        response.handle(Results.InternalServerError)
      }
    }

    onFinishService(execContext)

  }

  protected def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
    request.getQueryString match {
      case null | "" => Map.empty
      case queryString => queryString.replaceFirst("^?", "").split("&").map(_.split("=")).map { array =>
        array.length match {
          case 0 => None
          case 1 => Some(URLDecoder.decode(array(0), "UTF-8") -> "")
          case _ => Some(URLDecoder.decode(array(0), "UTF-8") -> URLDecoder.decode(array(1), "UTF-8"))
        }
      }.flatten.groupBy(_._1).map { case (key, value) => key -> value.map(_._2).toSeq }.toMap
    }
  }

  override def contextInitialized(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    // See https://github.com/dlecan/play2-war-plugin/issues/54
    // Store all handlers before Play Logger.configure(...)
    val julHandlers: Option[Array[Handler]] = Option(java.util.logging.Logger.getLogger("")).map { root =>
      root.getHandlers
    }
  }

  override def contextInitialized(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    // Init or get singleton
    Play2WarServer
  }

  override def contextDestroyed(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextDestroyed")

    Play2WarServer.stop(e.getServletContext)
  }

  override def destroy = {
    getServletContext.log("PlayServletWrapper > destroy")

    Play2WarServer.stop(getServletContext)
  }


}
