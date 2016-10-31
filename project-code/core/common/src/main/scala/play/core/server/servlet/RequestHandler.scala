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
import java.net.URLDecoder
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.http.{Cookie ⇒ ServletCookie}
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import play.api._
import play.api.Logger
import play.api.http._
import play.api.http.HeaderNames.CONTENT_LENGTH
import play.api.http.HeaderNames.X_FORWARDED_FOR
import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator
import play.api.libs.streams.Accumulator
import play.api.mvc.{WebSocket, _}
import play.core.server.common.ServerResultUtils

import scala.concurrent.Future
import scala.util.control.Exception
import scala.util.{Failure, Success}

trait RequestHandler {

  def apply(server: Play2WarServer)

}

private object HttpServletRequestHandler {
  val logger: Logger = Logger(classOf[HttpServletRequestHandler])
}

trait HttpServletRequestHandler extends RequestHandler {
  import HttpServletRequestHandler._

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

  /** Create the source for the request body */
  protected def convertRequestBody()(implicit mat: Materializer): Option[Source[ByteString, Any]] = {
    getHttpRequest().getRichInputStream.map { is ⇒
      StreamConverters.fromInputStream(() ⇒ is)
    }
  }

  protected def feedBodyParser(bodyParser: Iteratee[Array[Byte], Result]): Future[Result] = {
    // default synchronous blocking body enumerator

    // FIXME this default body enumerator reads the entire stream in memory
    // uploading a lot of data can lead to OutOfMemoryException
    // For more details: https://github.com/dlecan/play2-war-plugin/issues/223
    val bodyEnumerator = getHttpRequest().getRichInputStream.fold(Enumerator.eof[Array[Byte]]) { is =>
      val output = new java.io.ByteArrayOutputStream()
      val buffer = new Array[Byte](1024 * 8)
      var length = is.read(buffer)
      while(length != -1){
        output.write(buffer, 0, length)
        length = is.read(buffer)
      }
      Enumerator(output.toByteArray) andThen Enumerator.eof
    }

    bodyEnumerator |>>> bodyParser
  }

  protected def setHeaders(headers: Map[String, String], httpResponse: HttpServletResponse): Unit = {
    // Set response headers
    headers.foreach {
      case (CONTENT_LENGTH, "-1") => // why is it skip?

      // Fix a bug for Set-Cookie header.
      // Multiple cookies could be merged in a single header
      // but it's not properly supported by some browsers
      case (name, value) if name.equalsIgnoreCase(play.api.http.HeaderNames.SET_COOKIE) =>
        getServletCookies(value).foreach(httpResponse.addCookie)

      case (name, value) if name.equalsIgnoreCase(HeaderNames.TRANSFER_ENCODING) && value == HttpProtocol.CHUNKED =>
        // ignore this header
        // the JEE container sets this header itself. Avoid duplication of header (issues/289)

      case (name, value) =>
        httpResponse.setHeader(name, value)
    }
  }

  /**
   * default implementation to push a play result to the servlet output stream
   * @param futureResult the result of the play action
   * @param cleanup clean up callback
   */
  protected def pushPlayResultToServletOS(futureResult: Future[Result])(implicit mat: Materializer): Future[Unit] = {
    // TODO: should use the servlet thread here or use special thread pool for blocking IO operations
    // (https://github.com/dlecan/play2-war-plugin/issues/223)
    import play.api.libs.iteratee.Execution.Implicits.trampoline

    futureResult.map { result =>
      getHttpResponse().getHttpServletResponse.foreach { httpResponse =>

        val status = result.header.status
        val headers = result.header.headers
        val body: HttpEntity = result.body

        Logger("play").trace("Sending simple result: " + result)

        httpResponse.setStatus(status)

        setHeaders(headers, httpResponse)

        val withContentLength = headers.exists(_._1.equalsIgnoreCase(CONTENT_LENGTH))
        val chunked = headers.exists {
          case (key, value) => key.equalsIgnoreCase(HeaderNames.TRANSFER_ENCODING) && value == HttpProtocol.CHUNKED
        }

        // TODO do not allow chunked for http 1.0?
        // if (chunked && connection == KeepAlive) { send Results.HttpVersionNotSupported("The response to this request is chunked and hence requires HTTP 1.1 to be sent, but this is a HTTP 1.0 request.") }

        // Stream the result
//        if (withContentLength || chunked) {
          val flow = body.dataStream.map { f ⇒
            getHttpResponse().getRichOutputStream.foreach { os ⇒
              os.write(f.asByteBuffer.array())
              os.flush()
            }
          }
          val run = flow.runFold[Unit](()){(_,_) ⇒ ()}

          run.andThen {
            case Success(_) =>
//              cleanup()
              onHttpResponseComplete()
            case Failure(ex) =>
              Logger("play").debug(ex.toString)
              onHttpResponseComplete()
          }
//        } else {
//          Logger("play").trace("Result without Content-length")
//
//          // No Content-Length header specified, buffer in-memory
//          val byteBuffer = new ByteArrayOutputStream
//          val byteArrayOSIteratee = Iteratee.fold(byteBuffer)((b, e: Array[Byte]) => {
//            b.write(e); b
//          })
//
//          val p = body |>>> Enumeratee.grouped(byteArrayOSIteratee) &>> Cont {
//            case Input.El(buffer) =>
//              Logger("play").trace("Buffer size to send: " + buffer.size)
//              getHttpResponse().getHttpServletResponse.map { response =>
//                // set the content length ourselves
//                response.setContentLength(buffer.size)
//                val os = response.getOutputStream
//                os.flush()
//                buffer.writeTo(os)
//              }
//              val p = Future.successful(())
//              Iteratee.flatten(p.map(_ => Done(1, Input.Empty: Input[ByteArrayOutputStream])))
//
//            case other => Error("unexpected input", other)
//          }
//          p.andThen {
//            case Success(_) =>
//              cleanup()
//              onHttpResponseComplete()
//            case Failure(ex) =>
//              Logger("play").debug(ex.toString)
//              onHttpResponseComplete()
//          }
//        }

      } // end match foreach

    }
  }
}

/**
 * Generic implementation of HttpServletRequestHandler.
 * One instance per incoming HTTP request.
 *
 * <strong>/!\ Warning: this class and its subclasses are intended to thread-safe.</strong>
 */
abstract class Play2GenericServletRequestHandler(val servletRequest: HttpServletRequest, val servletResponse: Option[HttpServletResponse]) extends HttpServletRequestHandler {
  import HttpServletRequestHandler._

  override def apply(server: Play2WarServer) = {

    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val httpVersion = servletRequest.getProtocol
    val servletPath = servletRequest.getRequestURI
    val servletUri = servletPath + Option(servletRequest.getQueryString).filterNot(_.isEmpty).fold("")("?" + _)
    val parameters = getHttpParameters(servletRequest)
    val rHeaders = getPlayHeaders(servletRequest)
    val httpMethod = servletRequest.getMethod
    val isSecure = servletRequest.isSecure

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
        def secure: Boolean = isSecure
        def clientCertificateChain: Option[Seq[X509Certificate]] = None
      }
      untaggedRequestHeader
    }

    // get handler for request
    val (requestHeader, resultOrHandler: Either[Future[Result], (Handler,Application)]) = Exception
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
//    val alreadyClean = new java.util.concurrent.atomic.AtomicBoolean(false)
//    def cleanup() {
//      if (!alreadyClean.getAndSet(true)) {
//        play.api.Play.maybeApplication.foreach(_.global.onRequestCompletion(requestHeader))
//      }
//    }

    trait Response {
      def handle(result: Result): Unit
    }

//    def cleanFlashCookie(result: Result): Result = {
//      val header = result.header
//
//      val flashCookie = {
//        header.headers.get(HeaderNames.SET_COOKIE)
//          .map(Cookies.decode)
//          .flatMap(_.find(_.name == Flash.COOKIE_NAME)).orElse {
//            Option(requestHeader.flash).filterNot(_.isEmpty).map { _ =>
//              Flash.discard.toCookie
//            }
//          }
//      }
//
//      flashCookie.fold(result) { newCookie =>
//        result.withHeaders(HeaderNames.SET_COOKIE -> Cookies.merge(header.headers.getOrElse(HeaderNames.SET_COOKIE, ""), Seq(newCookie)))
//      }
//    }

    resultOrHandler match {

      //execute normal action
      case Right((action: EssentialAction, app)) =>
        val recovered = EssentialAction { rh =>
          import play.api.libs.iteratee.Execution.Implicits.trampoline
          action(rh).recoverWith {
            case error ⇒ app.errorHandler.onServerError(rh, error)
          }
        }
        handleAction(recovered, requestHeader, Some(app))

      //handle all websocket request as bad, since websocket are not handled
      //handle bad websocket request
      case Right((ws: WebSocket, app)) =>
        Logger("play").trace("Bad websocket request")
        val action = EssentialAction(_ => Accumulator.done(Results.BadRequest))
        handleAction(action, requestHeader, Some(app))

      case Left(e) =>
        Logger("play").trace("No handler, got direct result: " + e)
        import play.api.libs.iteratee.Execution.Implicits.trampoline
        val action = EssentialAction(_ => Accumulator.done(e))
        handleAction(action, requestHeader, None)
    }

//    def handleAction(action: EssentialAction, app: Option[Application]) {
//      val bodyParser = Iteratee.flatten(
//        scala.concurrent.Future(action(requestHeader))(play.api.libs.concurrent.Execution.defaultContext)
//      )
//
//      import play.api.libs.iteratee.Execution.Implicits.trampoline
//
//      // Remove Except: 100-continue handling, since it's impossible to handle it
//      //val expectContinue: Option[_] = requestHeader.headers.get("Expect").filter(_.equalsIgnoreCase("100-continue"))
//
//      val eventuallyResult: Future[Result] = feedBodyParser(bodyParser)
//
//      val eventuallyResultWithError = eventuallyResult.recoverWith {
//        case error =>
//          Logger("play").error("Cannot invoke the action, eventually got an error: " + error)
//          app.fold(DefaultGlobal.onError(requestHeader, error)) {
//            _.errorHandler.onServerError(requestHeader, error)
//          }
//      }.map { result => cleanFlashCookie(result) }
//
//      pushPlayResultToServletOS(eventuallyResultWithError, cleanup)
//    }

    /**
      * Handle an essential action.
      */
    def handleAction(
      action: EssentialAction,
      requestHeader: RequestHeader,
      app: Option[Application]
    ): Unit = {

      implicit val mat: Materializer = app.fold(server.materializer)(_.materializer)
      import play.api.libs.iteratee.Execution.Implicits.trampoline

      val body = convertRequestBody()
      val bodyParser = action(requestHeader)
      val resultFuture = body match {
        case None =>
          bodyParser.run()
        case Some(source) =>
          bodyParser.run(source)
      }

      val httpErrorHandler = app.fold[HttpErrorHandler](DefaultHttpErrorHandler)(_.errorHandler)

      resultFuture.recoverWith {
        case error =>
          logger.error("Cannot invoke the action", error)
          httpErrorHandler.onServerError(requestHeader, error)
      }.map {
        result =>
          val cleanedResult = ServerResultUtils.cleanFlashCookie(requestHeader, result)
          val validated = ServerResultUtils.validateResult(requestHeader, cleanedResult, httpErrorHandler)
          pushPlayResultToServletOS(validated)
      }
    }

    onFinishService()
  }

  private def getHttpParameters(request: HttpServletRequest): Map[String, Seq[String]] = {
    request.getQueryString match {
      case null | "" => Map.empty
      case queryString => queryString.replaceFirst("^?", "").split("&").flatMap { queryElement =>
        val array = queryElement.split("=")
        array.length match {
          case 0 => None
          case 1 => Some(URLDecoder.decode(array(0), "UTF-8") -> "")
          case _ => Some(URLDecoder.decode(array(0), "UTF-8") -> URLDecoder.decode(array(1), "UTF-8"))
        }
      }.groupBy(_._1).map { case (key, value) => key -> value.map(_._2).toSeq }
    }
  }

}
