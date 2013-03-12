package play.core.server.servlet

import javax.servlet.http.{ Cookie => ServletCookie }
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import play.api._
import play.api.mvc._
import java.util.concurrent.atomic.AtomicBoolean
import play.api.libs.iteratee.Enumerator
import play.api.Logger
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import scala.concurrent.Future
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import play.api.http.HeaderNames

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

abstract class Play2GenericServletRequestHandler(val servletRequest: HttpServletRequest, val servletResponse: Option[HttpServletResponse]) extends HttpServletRequestHandler {

  implicit val internalExecutionContext = play.core.Execution.internalContext

  private val requestIDs = new java.util.concurrent.atomic.AtomicLong(0)

  override def apply(server: Play2WarServer) = {

    val server = Play2WarServer.playServer

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val httpVersion = servletRequest.getProtocol.substring("HTTP/".length, servletRequest.getProtocol.length)
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

    val untaggedRequestHeader = new RequestHeader {
      val id = requestIDs.incrementAndGet
      val tags = Map.empty[String, String]
      def uri = servletUri
      def path = servletPath
      def method = httpMethod
      val version = httpVersion
      def queryString = parameters
      def headers = rHeaders
      lazy val remoteAddress = rRemoteAddress
      def username = None

      override def toString = {
        super.toString + "\nURI: " + uri + "\nMethod: " + method + "\nPath: " + path + "\nParameters: " + queryString + "\nHeaders: " + headers + "\nCookies: " + rCookies
      }
    }

    // get handler for request
    val handler = server.getHandlerFor(untaggedRequestHeader)

    // tag request if necessary
    val requestHeader = handler.right.toOption.map({
      case (h: RequestTaggingHandler, _) => h.tagRequest(untaggedRequestHeader)
      case _ => untaggedRequestHeader
    }).getOrElse(untaggedRequestHeader)

    Logger("play").trace("HTTP request headers: " + requestHeader)

    // Call onRequestCompletion after all request processing is done. Protected with an AtomicBoolean to ensure can't be executed more than once.
    val alreadyClean = new java.util.concurrent.atomic.AtomicBoolean(false)
    def cleanup() {
      if (!alreadyClean.getAndSet(true)) {
        play.api.Play.maybeApplication.foreach(_.global.onRequestCompletion(requestHeader))
      }
    }

    // converting servlet response to play's
    val response = new Response {

      def handle(result: Result) {

        getHttpResponse().getHttpServletResponse.foreach { httpResponse =>

          result match {

            case AsyncResult(p) => p.extend1 {
              case Redeemed(v) => handle(v)
              case Thrown(e) => {
                server.applicationProvider.get match {
                  case Right(app) => handle(app.handleError(requestHeader, e))
                  case Left(_) => handle(Results.InternalServerError)
                }
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
                //
                //                val writer: Function1[r.BODY_CONTENT, Future[Unit]] = x => {
                //                  Promise.pure(
                //                    {
                //                      if (hasError.get) {
                //                        ()
                //                      } else {
                //                        getHttpResponse().getRichOutputStream.foreach { os =>
                //                          os.write(r.writeable.transform(x))
                //                          os.flush
                //                        }
                //                      }
                //                    }).extend1 {
                //                      case Redeemed(()) => ()
                //                      case Thrown(ex) => {
                //                        hasError.set(true)
                //                        Logger("play").debug("Exception received while writing to client: " + ex.toString)
                //                      }
                //                    }
                //                }
                //
                //                val bodyIteratee = {
                //                  val writeIteratee = Iteratee.fold1(
                //                    Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))
                //
                //                  Enumeratee.breakE[r.BODY_CONTENT](_ => hasError.get)(writeIteratee).mapDone { _ =>
                //                    onHttpResponseComplete()
                //                  }
                val bodyIteratee = {
                  def step(in: Input[r.BODY_CONTENT]): Iteratee[r.BODY_CONTENT, Unit] = (!hasError.get, in) match {
                    case (true, Input.El(x)) =>
                      Iteratee.flatten(
                        Promise.pure(
                          if (hasError.get) {
                            ()
                          } else {
                            getHttpResponse().getRichOutputStream.foreach { os =>
                              os.write(r.writeable.transform(x))
                              os.flush
                            }
                          })
                          //                          .map(_ => if (!hasError.get) Cont(step) else Done((), Input.Empty)))
                          .extend1 {
                            case Redeemed(_) => if (!hasError.get) Cont(step) else Done((), Input.Empty)
                            case Thrown(ex) =>
                              hasError.set(true)
                              Logger("play").debug(ex.toString)
                              throw ex
                          })
                    case (true, Input.Empty) => Cont(step)
                    case (_, in) => Done((), in)
                  }
                  Iteratee.flatten(
                    Promise.pure(())
                      .map(_ => if (!hasError.get) Cont(step) else Done((), Input.Empty: Input[r.BODY_CONTENT])))
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
                val writer: Function2[ByteArrayOutputStream, r.BODY_CONTENT, Unit] = (b, x) => b.write(r.writeable.transform(x))
                val stringIteratee = Iteratee.fold(byteBuffer)((b, e: r.BODY_CONTENT) => { writer(b, e); b })
                //                val p = body |>> stringIteratee
                //
                //                p.flatMap(i => i.run)
                //                  .onRedeem { buffer =>
                //                    Logger("play").trace("Buffer size to send: " + buffer.size)
                //                    getHttpResponse().getRichOutputStream.map { os =>
                //                      getHttpResponse().getHttpServletResponse.map(_.setContentLength(buffer.size))
                //                      os.flush
                //                      buffer.writeTo(os)
                //                    }
                //                    onHttpResponseComplete()
                //                  }
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

                  case other => Error("unexepected input", other)
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

              //              val writer: Function1[r.BODY_CONTENT, Future[Unit]] = x => {
              //                Promise.pure(
              //                  {
              //                    if (hasError.get) {
              //                      ()
              //                    } else {
              //                      getHttpResponse().getRichOutputStream.foreach { os =>
              //                        os.write(r.writeable.transform(x))
              //                        os.flush
              //                      }
              //                    }
              //                  }).extend1 {
              //                    case Redeemed(()) => ()
              //                    case Thrown(ex) => {
              //                      hasError.set(true)
              //                      Logger("play").debug("Exception received while writing to client: " + ex.toString)
              //                    }
              //                  }
              //              }
              //
              //              val chunksIteratee = {
              //                val writeIteratee = Iteratee.fold1(
              //                  Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))
              //
              //                Enumeratee.breakE[r.BODY_CONTENT](_ => hasError.get)(writeIteratee).mapDone { _ =>
              //                  onHttpResponseComplete()
              //                }
              //              }
              //
              //              chunks(chunksIteratee)
              val bodyIteratee = {
                def step(in: Input[r.BODY_CONTENT]): Iteratee[r.BODY_CONTENT, Unit] = (!hasError.get, in) match {
                  case (true, Input.El(x)) =>
                    Iteratee.flatten(
                      Promise.pure(
                        if (hasError.get) {
                          ()
                        } else {
                          getHttpResponse().getRichOutputStream.foreach { os =>
                            os.write(r.writeable.transform(x))
                            os.flush
                          }
                        })
                        .extend1 {
                          case Redeemed(_) => if (!hasError.get) Cont(step) else Done((), Input.Empty)
                          case Thrown(ex) =>
                            Logger("play").debug(ex.toString)
                            hasError.set(true)
                            throw ex
                        })
                  case (true, Input.Empty) => Cont(step)
                  case (_, in) => Done((), in)
                }
                Iteratee.flatten(Promise.pure()
                  .extend1 {
                    case Redeemed(_) => if (!hasError.get) Cont(step) else Done((), Input.Empty: Input[r.BODY_CONTENT])
                    case Thrown(ex) =>
                      Logger("play").debug(ex.toString)
                      hasError.set(true)
                      throw ex
                  })
              }

              chunks apply bodyIteratee.map { _ =>
                cleanup()
                onHttpResponseComplete
              }
            }

            case unknownResponse =>
              Logger("play").error("Unhandle default response: " + unknownResponse)

              httpResponse.setContentLength(0);
              httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
              onHttpResponseComplete()

          } // end match result

        } // end match foreach

      } // end handle method

    }

    def cleanFlashCookie(r: PlainResult): Result = {
      val header = r.header

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
        r.withHeaders(HeaderNames.SET_COOKIE -> Cookies.merge(header.headers.get(HeaderNames.SET_COOKIE).getOrElse(""), Seq(newCookie)))
      }.getOrElse(r)
    }

    handler match {

      //execute normal action
      case Right((action: EssentialAction, app)) =>
        val a = EssentialAction { rh =>
          Iteratee.flatten(action(rh).unflatten. /*map {
            case r: PlainResult => cleanFlashCookie(r)
            case a: AsyncResult => a.transform(cleanFlashCookie)
          }.*/ extend1 {
              case Redeemed(it) => it.it
              case Thrown(e) => Done(app.handleError(requestHeader, e), Input.Empty)
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
        val a = EssentialAction(_ => Done(e, Input.Empty))
        handleAction(a, None)

      case unexpected =>
        Logger("play").error("Oops, unexpected message received in Play server (please report this problem): " + unexpected)
        response.handle(Results.InternalServerError)
    }

    def handleAction(a: EssentialAction, app: Option[Application]) {
      Logger("play").trace("Serving this request with: " + a)

      val filteredAction = app.map(_.global).getOrElse(DefaultGlobal).doFilter(a)

      val eventuallyBodyParser = scala.concurrent.Future(filteredAction(requestHeader))(play.api.libs.concurrent.Execution.defaultContext)

      // Remove Except: 100-continue handling, since it's impossible to handle it
      // requestHeader.headers.get("Expect").filter(_ == "100-continue")

      val bodyEnumerator = getHttpRequest().getRichInputStream.map { is =>
        Enumerator.fromStream(is).andThen(Enumerator.eof)
      }.getOrElse(Enumerator.eof)

      val eventuallyResultIteratee = eventuallyBodyParser.flatMap(it => bodyEnumerator |>> it): scala.concurrent.Future[Iteratee[Array[Byte], Result]]

      val eventuallyResult = eventuallyResultIteratee.flatMap(it => it.run)
      eventuallyResult.extend1 {
        case Redeemed(result) => response.handle(result)

        case Thrown(error) =>
          Logger("play").error("Cannot invoke the action, eventually got an error: " + error)
          response.handle( app.map(_.handleError(requestHeader, error)).getOrElse(DefaultGlobal.onError(requestHeader, error)))
      }
    }

    onFinishService()

  }

  override protected def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
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

}
