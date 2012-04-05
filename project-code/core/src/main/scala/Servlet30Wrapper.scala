package play.core.server.servlet

import javax.servlet._
import javax.servlet.annotation._
import javax.servlet.http._
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
import play.core.server.servlet._
import server.Server

import scala.collection.JavaConverters._

object Servlet30Wrapper {
  var playServer: Play2WarServer = null
}

@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Servlet30Wrapper extends HttpServlet with ServletContextListener with Helpers {

  protected override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) = {
    Logger("play").trace("Http request received: " + servletRequest)

    val aSyncContext = servletRequest.startAsync
    val aSyncCtxListener = new ASyncCtxListener
    aSyncContext.addListener(aSyncCtxListener)

    val server = Servlet30Wrapper.playServer

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val version = servletRequest.getProtocol.substring("HTTP/".length, servletRequest.getProtocol.length)
    val servletUri = servletRequest.getServletPath
    val parameters = Map.empty[String, Seq[String]] ++ servletRequest.getParameterMap.asScala.mapValues(Arrays.asList(_: _*).asScala)
    val rHeaders = getPlayHeaders(servletRequest)
    val rCookies = getPlayCookies(servletRequest)
    val httpMethod = servletRequest.getMethod

    val requestHeader = new RequestHeader {
      def uri = servletUri
      def path = uri
      def method = httpMethod
      def queryString = parameters
      def headers = rHeaders
      def username = None

      override def toString = {
        super.toString + "\n" + path + "\n" + queryString + "\n" + headers + "\n" + rCookies
      }
    }
    Logger("play").trace("requestHeader: " + requestHeader)

    // converting servlet response to play's
    val response = new Response {

      def handle(result: Result) {

        aSyncContext.getResponse match {
          
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

                // Set response headers
                headers.filterNot(_ == (CONTENT_LENGTH, "-1")).foreach {

                  // Fix a bug for Set-Cookie header. 
                  // Multiple cookies could be merged in a single header
                  // but it's not properly supported by some browsers
                  case (name @ play.api.http.HeaderNames.SET_COOKIE, value) => {
                    // TODO
                    //                    nettyResponse.setHeader(name, Cookies.decode(value).map { c => Cookies.encode(Seq(c)) }.asJava)
                  }

                  case (name, value) => httpResponse.setHeader(name, value)
                }

                // Stream the result
                headers.get(CONTENT_LENGTH).map { contentLength =>
                  Logger("play").trace("Result with Content-length: " + contentLength)

                  val writer: Function2[AsyncContext, r.BODY_CONTENT, Unit] = (a, x) => {
                    a.getResponse.getOutputStream.write(r.writeable.transform(x))
                  }
                  val bodyIteratee = Iteratee.fold(aSyncContext)((a, e: r.BODY_CONTENT) => { writer(a, e); a })
                  val p = body |>> bodyIteratee

                  p.flatMap(i => i.run)
                    .onRedeem { buffer =>
                      aSyncContext.complete()
                    }
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
                      aSyncContext.getResponse.setContentLength(buffer.size)
                      buffer.writeTo(aSyncContext.getResponse.getOutputStream)
                      aSyncContext.complete()
                    }
                }
              }

              case defaultResponse @ _ =>
                Logger("play").trace("Default response: " + defaultResponse)
                httpResponse.setContentLength(0);
                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                aSyncContext.complete()
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

        //                    e.getChannel.setReadable(false)
        //
        //                    ctx.setAttachment(scala.collection.mutable.ListBuffer.empty[org.jboss.netty.channel.MessageEvent])
        //
        val eventuallyBodyParser = server.getBodyParser[action.BODY_CONTENT](requestHeader, bodyParser)

        Logger("play").trace("Before: eventuallyResultOrBody")
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
            //
            //                if (nettyHttpRequest.isChunked) {
            //
            //                  val (result, handler) = newRequestBodyHandler(bodyParser, allChannels, server)
            //
            //                  val intermediateChunks = ctx.getAttachment.asInstanceOf[scala.collection.mutable.ListBuffer[org.jboss.netty.channel.MessageEvent]]
            //                  intermediateChunks.foreach(handler.messageReceived(ctx, _))
            //                  ctx.setAttachment(null)
            //
            //                  val p: ChannelPipeline = ctx.getChannel().getPipeline()
            //                  p.replace("handler", "handler", handler)
            //                  e.getChannel.setReadable(true)
            //
            //                  result
            //                } else {
            //                  e.getChannel.setReadable(true)

            lazy val bodyEnumerator = {
              val body = Stream.continually(aSyncContext.getRequest.getInputStream.read).takeWhile(-1 !=).map(_.toByte).toArray
              Enumerator(body).andThen(Enumerator.enumInput(EOF))
            }

            try {
              (bodyEnumerator |>> bodyParser): Promise[Iteratee[Array[Byte], Either[Result, action.BODY_CONTENT]]]
            } finally {
              aSyncContext.getRequest.getInputStream.close
            }
            //                            }
          }
        Logger("play").trace("After: eventuallyResultOrBody: " + eventuallyResultOrBody)

        Logger("play").trace("Before: eventuallyResultOrRequest")

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
                  val body = b
                })
            }

        Logger("play").trace("After: eventuallyResultOrRequest: " + eventuallyResultOrRequest)

        Logger("play").trace("Before: eventuallyResultOrRequest.extend")

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
            //            e.getChannel.setReadable(true)
          }
        })
        Logger("play").trace("After: eventuallyResultOrRequest.extend")

      }

      //execute websocket action
      case Right((ws @ WebSocket(f), app)) /* if (websocketableRequest.check)*/ => {

        Logger("play").trace("Serving this request with: " + ws)
        //
        //            try {
        //              val enumerator = websocketHandshake(ctx, nettyHttpRequest, e)(ws.frameFormatter)
        //              f(requestHeader)(enumerator, socketOut(ctx)(ws.frameFormatter))
        //            } catch {
        //              case e => e.printStackTrace
        //            }
        //          }
        //
        //          //handle bad websocket request
        //          case Right((WebSocket(_), _)) => {
        //
        //            Logger("play").trace("Bad websocket request")
        //
        //            response.handle(Results.BadRequest)
        //          }
        //
        //          //handle errors
        //          case Left(e) => {
        //
        //            Logger("play").trace("No handler, got direct result: " + e)
        //
        //            response.handle(e)
        //          }
        //
      }
      //
      //      case chunk: org.jboss.netty.handler.codec.http.HttpChunk => {
      //        val intermediateChunks = ctx.getAttachment.asInstanceOf[scala.collection.mutable.ListBuffer[org.jboss.netty.channel.MessageEvent]]
      //        if (intermediateChunks != null) {
      //          intermediateChunks += e
      //          ctx.setAttachment(intermediateChunks)
      //        }
      //      }
      //
      case unexpected => Logger("play").error("Oops, unexpected message received in Play server (please report this problem): " + unexpected)
      //
    }

  }

  override def contextInitialized(e: ServletContextEvent) = {
    e.getServletContext.log("PlayServletWrapper > contextInitialized")

    Logger.configure(Map.empty, Map.empty, Mode.Prod)

    val classLoader = e.getServletContext.getClassLoader;

    Servlet30Wrapper.playServer = new Play2WarServer(new WarApplication(classLoader, Mode.Prod))
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
    Option(Servlet30Wrapper.playServer).map {
      s =>
        s.stop()
        Servlet30Wrapper.playServer = null
        sc.log("Play server stopped")
    } // if playServer is null, nothing to do
  }
}

class ASyncCtxListener extends AsyncListener {

  var eventReceived: Boolean = false

  override def onComplete(event: AsyncEvent) = {
    eventReceived = true;
  }

  override def onTimeout(event: AsyncEvent) = {
    eventReceived = true;
  }

  override def onError(event: AsyncEvent) = {
    eventReceived = true;
  }

  override def onStartAsync(event: AsyncEvent) = {
  }

}