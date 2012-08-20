package play.core.server.servlet

import javax.servlet._
import javax.servlet.http.{ Cookie => ServletCookie, _ }
import java.io._
import java.util.Arrays

import play.api._
import play.api.mvc._
import play.api.http._
import play.api.http.HeaderNames._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._
import play.core._
import server.Server

import scala.collection.JavaConverters._

object Play2Servlet {
  var playServer: Play2WarServer = null
}

/**
 * Mother class for all servlet implementations for Play2.
 */
abstract class Play2Servlet[T] extends HttpServlet with ServletContextListener {
  
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
  protected def getHttpRequest(execContext: T): HttpServletRequest

  /**
   * Get HTTP response.
   */
  protected def getHttpResponse(execContext: T): HttpServletResponse

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
    
    val server = Play2Servlet.playServer

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val version = servletRequest.getProtocol.substring("HTTP/".length, servletRequest.getProtocol.length)
    val servletUri = servletRequest.getRequestURI + (if(servletRequest.getQueryString == null) "" else "?" + servletRequest.getQueryString)
    val parameters = getHttpParameters(servletRequest)
    val rHeaders = getPlayHeaders(servletRequest)
    val rCookies = getPlayCookies(servletRequest)
    val httpMethod = servletRequest.getMethod
    val rRemoteAddress = servletRequest.getRemoteAddr

    val requestHeader = new RequestHeader {
      def uri = servletUri
      def path = uri
      def method = httpMethod
      def queryString = parameters
      def headers = rHeaders
      def username = None
      def remoteAddress = rRemoteAddress

      override def toString = {
        super.toString + "\nPath: " + path + "\nParameters: " + queryString + "\nHeaders: " + headers + "\nCookies: " + rCookies
      }
    }
    Logger("play").trace("HTTP request content: " + requestHeader)

    // converting servlet response to play's
    val response = new Response {

      def handle(result: Result) {

        getHttpResponse(execContext) match {

          // Handle only HttpServletResponse
          case httpResponse: HttpServletResponse => {

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

                  val writer: Function1[r.BODY_CONTENT, Promise[Unit]] = x => {
                    Promise.pure(
                      {
                        getHttpResponse(execContext).getOutputStream.write(r.writeable.transform(x))
                        getHttpResponse(execContext).getOutputStream.flush
                      }).extend1 { case Redeemed(()) => (); case Thrown(ex) => Logger("play").debug(ex.toString) }
                  }

                  val bodyIteratee = {
                    val writeIteratee = Iteratee.fold1(
                      Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))

                    writeIteratee.mapDone { _ =>
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
                      getHttpResponse(execContext).setContentLength(buffer.size)
                      getHttpResponse(execContext).getOutputStream.flush
                      buffer.writeTo(getHttpResponse(execContext).getOutputStream)
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

                val writer: Function1[r.BODY_CONTENT, Promise[Unit]] = x => {
                  Promise.pure(
                    {
                      getHttpResponse(execContext).getOutputStream.write(r.writeable.transform(x))
                      getHttpResponse(execContext).getOutputStream.flush
                    }).extend1 { case Redeemed(()) => (); case Thrown(ex) => Logger("play").debug(ex.toString) }
                }

                val chunksIteratee = {
                  val writeIteratee = Iteratee.fold1(
                    Promise.pure(()))((_, e: r.BODY_CONTENT) => writer(e))

                  writeIteratee.mapDone { _ =>
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

          } // end case HttpServletResponse

          case unexpected => Logger("play").error("Oops, unexpected message received in Play server (please report this problem): " + unexpected)

        } // end match getResponse
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

        val eventuallyResultOrBody =
          eventuallyBodyParser.flatMap { bodyParser =>

            requestHeader.headers.get("Expect") match {
              case Some("100-continue") => {
                bodyParser.fold(
                  (_, _) => Promise.pure(()),
                  k => {
                    //                        val continue = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)
                    //                        e.getChannel.write(continue)
                    Promise.pure(())
                  },
                  (_, _) => Promise.pure(()))

              }
              case _ => Logger("play").trace("Expect header:" + requestHeader.headers.get("Expect"))
            }

            lazy val bodyEnumerator = {
              val body = Stream.continually(getHttpRequest(execContext).getInputStream.read).takeWhile(-1 !=).map(_.toByte).toArray
              Enumerator(body).andThen(Enumerator.enumInput(EOF))
            }

            try {
              (bodyEnumerator |>> bodyParser): Promise[Iteratee[Array[Byte], Either[Result, action.BODY_CONTENT]]]
            } finally {
              getHttpRequest(execContext).getInputStream.close
            }
            //                            }
          }

        val eventuallyResultOrRequest =
          eventuallyResultOrBody
            .flatMap(it => it.run)
            .map {
              _.right.map(b =>
                new Request[action.BODY_CONTENT] {
                  def uri = servletUri
                  def path = servletUri
                  def method = httpMethod
                  def queryString = parameters
                  def headers = rHeaders
                  def username = None
                  def remoteAddress = rRemoteAddress
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

  override def contextInitialized(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    Logger.configure(Map.empty, Map.empty, Mode.Prod)

    val classLoader = getClass.getClassLoader;

    Play2Servlet.playServer = new Play2WarServer(new WarApplication(classLoader, Mode.Prod))
  }

  override def contextDestroyed(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextDestroyed")

    stopPlayServer(e.getServletContext)
  }

  override def destroy = {
    getServletContext.log("PlayServletWrapper > destroy")

    stopPlayServer(getServletContext)
  }

  private def stopPlayServer(sc: ServletContext) = {
    Option(Play2Servlet.playServer).map {
      s =>
        s.stop()
        Play2Servlet.playServer = null
        sc.log("Play server stopped")
    } // if playServer is null, nothing to do
  }
}
