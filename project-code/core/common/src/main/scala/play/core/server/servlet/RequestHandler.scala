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

import akka.stream.{Graph, Materializer, Shape}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, StreamConverters}
import akka.util.ByteString
import javax.servlet.http.{HttpServletRequest, HttpServletResponse, Cookie => ServletCookie}
import play.api.{Logger, _}
import play.api.http.HeaderNames.{CONTENT_LENGTH, CONTENT_TYPE, X_FORWARDED_FOR}
import play.api.http.HttpEntity.Chunked
import play.api.http._
import play.api.libs.streams.Accumulator
import play.api.libs.typedmap.{TypedEntry, TypedMap}
import play.api.mvc._
import play.api.mvc.request.{RemoteConnection, RequestAttrKey, RequestTarget}

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
  import HttpServletRequestHandler.logger

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

  /** Create the sink for the response body */
  protected def convertResponseBody()(implicit mat: Materializer): Option[Sink[ByteString, Any]] = {
    getHttpResponse().getRichOutputStream.map { os ⇒
      StreamConverters.fromOutputStream(() ⇒ os)
    }
  }

//  // TODO #321 delete?
//  @deprecated
//  protected def feedBodyParser(bodyParser: Sink[ByteString, Result]): Future[Result] = {
//    getHttpRequest().getRichInputStream.map { is =>
//      StreamConverters.fromInputStream( () => is).toMat(bodyParser)
//    }
////    val bodySource = getHttpRequest().getRichInputStream.fold(Source.empty[Array[Byte]]) { is =>
////      val output = new java.io.ByteArrayOutputStream()
////      val buffer = new Array[Byte](1024 * 8)
////      var length = is.read(buffer)
////      while(length != -1){
////        output.write(buffer, 0, length)
////        length = is.read(buffer)
////      }
////      Enumerator(output.toByteArray) andThen Enumerator.eof
////    }
////
////    bodyEnumerator |>>> bodyParser
//  }

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
   */
  protected def pushPlayResultToServletOS(futureResult: Future[Result])(implicit mat: Materializer): Unit = {
    // TODO: should use the servlet thread here or use special thread pool for blocking IO operations
    // (https://github.com/dlecan/play2-war-plugin/issues/223)
    import play.api.libs.streams.Execution.Implicits.trampoline

    futureResult.map { result =>
      getHttpResponse().getHttpServletResponse.foreach { httpResponse =>

        val status = result.header.status
        val headers = result.header.headers
        val body: HttpEntity = result.body

        logger.trace("Sending simple result: " + result)

        httpResponse.setStatus(status)

        setHeaders(headers, httpResponse)
        body.contentLength.foreach { contentLength =>
          httpResponse.addHeader(CONTENT_LENGTH, contentLength.toString)
        }
        body.contentType.foreach { contentType =>
          httpResponse.addHeader(CONTENT_TYPE, contentType)
        }

        val withContentLength = body.contentLength.isDefined
        val chunked = body.isInstanceOf[Chunked]

        // TODO do not allow chunked for http 1.0?
        // if (chunked && connection == KeepAlive) { send Results.HttpVersionNotSupported("The response to this request is chunked and hence requires HTTP 1.1 to be sent, but this is a HTTP 1.0 request.") }

        val source: Source[ByteString, _] = body.dataStream

        if (withContentLength || chunked) {
          val sourceWithTermination: Source[ByteString, _] = source.watchTermination() { (_, done) =>
            done.onComplete {
              case Success(_) =>
                onHttpResponseComplete()
              case Failure(ex) =>
                logger.debug(ex.toString)
                onHttpResponseComplete()
            }
          }
          val sink: Sink[ByteString, Any] = convertResponseBody().getOrElse(Sink.ignore)
          val flow: RunnableGraph[Any] = sourceWithTermination.toMat(sink)(Keep.right)
          flow.run()
        } else {
          // No Content-Length header specified, buffer in-memory
          val buffer = new ByteArrayOutputStream
          val sink: Sink[ByteString, Future[ByteArrayOutputStream]] = Sink.fold[ByteArrayOutputStream, ByteString](buffer)((b, e) => {
            buffer.write(e.toArray); b
          })

          val flow = source.toMat(sink)(Keep.right)
          flow.run().andThen {
            case Success(_) =>
              logger.trace(s"Buffer size to send: ${buffer.size()}")
              getHttpResponse.getHttpServletResponse.map { response =>
                // set the content length ourselves
                response.setContentLength(buffer.size)
                val os = response.getOutputStream
                os.flush()
                buffer.writeTo(os)
              }
              onHttpResponseComplete()
            case Failure(ex) =>
              logger.debug(ex.toString)
              onHttpResponseComplete()
          }

        }

        // TODO #321 delete?
//        // Stream the result
//        if (withContentLength || chunked) {
//
//          val hasError: AtomicBoolean = new AtomicBoolean(false)
//
//          val bodyIteratee: Iteratee[Array[Byte], Unit] = {
//
//            def step(in: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = (!hasError.get, in) match {
//              case (true, Input.El(x)) =>
//                Iteratee.flatten(
//                  Future.successful(
//                    if (hasError.get) {
//                      ()
//                    } else {
//                      getHttpResponse().getRichOutputStream.map {
//                        os =>
//                          os.write(x)
//                          os.flush()
//                      }
//                    })
//                    .map { _ => if (!hasError.get) Cont(step) else Done((), Input.Empty: Input[Array[Byte]]) }
//                    .andThen {
//                      case Failure(ex) =>
//                        hasError.set(true)
//                        Logger("play").debug(ex.toString)
//                        throw ex
//                    })
//              case (true, Input.Empty) => Cont(step)
//              case (_, inp) => Done((), inp)
//            }
//            Iteratee.flatten(
//              Future.successful(())
//                .map(_ => if (!hasError.get) Cont(step) else Done((), Input.Empty: Input[Array[Byte]])))
//          }
//
//          val bodyConsumer = if (chunked) {
//            // if the result body is chunked, the chunks are already encoded with metadata in Results.chunk
//            // The problem is that the servlet container adds metadata again, leading the chunks encoded 2 times.
//            // As workaround, we 'dechunk' the body one time before sending it to the servlet container
//            body &> Results.dechunk |>>> bodyIteratee
//          } else {
//            body |>>> bodyIteratee
//          }
//          bodyConsumer.andThen {
//            case Success(_) =>
//              cleanup()
//              onHttpResponseComplete()
//            case Failure(ex) =>
//              Logger("play").debug(ex.toString)
//              hasError.set(true)
//              onHttpResponseComplete()
//          }
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

  override def apply(server: Play2WarServer): Unit = {
    //    val keepAlive -> non-sens
    //    val websocketableRequest -> non-sens
    val httpVersion = servletRequest.getProtocol
    val servletPath = servletRequest.getRequestURI
    val servletUri = servletPath + Option(servletRequest.getQueryString).filterNot(_.isEmpty).fold("")("?" + _)
    val parameters = getHttpParameters(servletRequest)
    val rHeaders = getPlayHeaders(servletRequest)
    val httpMethod = servletRequest.getMethod
    val isSecure = servletRequest.isSecure

    def rRemoteAddress: String = {
      val remoteAddress = servletRequest.getRemoteAddr
      (for {
        xff <- rHeaders.get(X_FORWARDED_FOR)
        app <- server.applicationProvider.get.toOption
        trustxforwarded <- app.configuration.getOptional[Boolean]("trustxforwarded").orElse(Some(false))
        if remoteAddress == "127.0.0.1" || trustxforwarded
      } yield xff).getOrElse(remoteAddress)
    }

    def tryToCreateRequest: RequestHeader = createRequestHeader(parameters)

    def createRequestHeader(parameters: Map[String, Seq[String]] = Map.empty[String, Seq[String]]): RequestHeader = {
      //mapping servlet request to Play's
      val untaggedRequestHeader = new RequestHeader {
        override def connection: RemoteConnection = RemoteConnection(rRemoteAddress, isSecure, None)
        override def method: String = httpMethod
        override def target: RequestTarget = RequestTarget(servletUri, servletPath, parameters)
        override def version: String = httpVersion
        override def headers: Headers = rHeaders
        override def attrs: TypedMap = TypedMap(TypedEntry(RequestAttrKey.Id, server.newRequestId))
      }
      untaggedRequestHeader
    }

    // get handler for request
    val (requestHeader, handler: Either[Future[Result], (Handler,Application)]) = Exception
      .allCatch[RequestHeader].either(tryToCreateRequest)
      .fold(
      e => {
        val rh = createRequestHeader()
        val r = server.applicationProvider.application.errorHandler.onClientError(rh, Status.BAD_REQUEST, e.getMessage)
        (rh, Left(r))
      },
      rh => server.getHandlerFor(rh) match {
        case directResult @ Left(_) => (rh, directResult)
        case Right((taggedRequestHeader, handler, application)) => (taggedRequestHeader, Right((handler, application)))
      }
    )

    // TODO #321 delete?
//    // Call onRequestCompletion after all request processing is done. Protected with an AtomicBoolean to ensure can't be executed more than once.
//    val alreadyClean = new java.util.concurrent.atomic.AtomicBoolean(false)
//    def cleanup() {
//      if (!alreadyClean.getAndSet(true)) {
//        play.api.Play.maybeApplication.foreach(_.global.onRequestCompletion(requestHeader))
//      }
//    }

    trait Response {
      def handle(result: Result): Unit
    }

    def cleanFlashCookie(result: Result): Result = {
      val header = result.header

      val flashCookie = {
        header.headers.get(HeaderNames.SET_COOKIE)
          .map(Cookies.decodeSetCookieHeader)
          .flatMap(_.find(_.name == Flash.COOKIE_NAME)).orElse {
            Option(requestHeader.flash).filterNot(_.isEmpty).map { _ =>
              Flash.discard.toCookie
            }
          }
      }

      flashCookie.fold(result) { newCookie =>
        result.withHeaders(HeaderNames.SET_COOKIE -> Cookies.mergeSetCookieHeader(header.headers.getOrElse(HeaderNames.SET_COOKIE, ""), Seq(newCookie)))
      }
    }

    handler match {

      //execute normal action
      case Right((action: EssentialAction, app)) =>
        val recovered = EssentialAction { rh =>
          import play.api.libs.streams.Execution.Implicits.trampoline
          action(rh).recoverWith {
            case error => app.errorHandler.onServerError(rh, error)
          }

          // TODO #321 delete?
//          import play.api.libs.iteratee.Execution.Implicits.trampoline
//          Iteratee.flatten(action(rh).unflatten.map(_.it).recover {
//            case error =>
//              Iteratee.flatten(
//                app.errorHandler.onServerError(requestHeader, error).map(result => Done(result, Input.Empty))
//              ): Iteratee[Array[Byte], Result]
//          })
        }
        handleAction(recovered, requestHeader, Some(app))

      //handle all websocket request as bad, since websocket are not handled
      //handle bad websocket request
      case Right((ws: WebSocket, app)) =>
        logger.trace("Bad websocket request")
        val action = EssentialAction(_ => Accumulator.done(Results.BadRequest))
        handleAction(action, requestHeader, Some(app))

      case Left(e) =>
        logger.trace("No handler, got direct result: " + e)
        val action = EssentialAction(_ => Accumulator.done(e))
        handleAction(action, requestHeader, None)
    }
    /**
      * Handle an essential action.
      */
    def handleAction( action: EssentialAction,
                      requestHeader: RequestHeader,
                      app: Option[Application]): Unit = {

      implicit val mat: Materializer = app.fold(server.materializer)(_.materializer)

      val body = convertRequestBody()
      val bodyParser = action(requestHeader)
      val resultFuture = body match {
        case None =>
          bodyParser.run()
        case Some(source) =>
          bodyParser.run(source)
      }

      val httpErrorHandler = app.fold[HttpErrorHandler](DefaultHttpErrorHandler)(_.errorHandler)

      import play.api.libs.streams.Execution.Implicits.trampoline

      val eventuallyResultWithError = resultFuture.recoverWith {
        case error =>
          logger.error("Cannot invoke the action", error)
          httpErrorHandler.onServerError(requestHeader, error)
      }.map(cleanFlashCookie)

      pushPlayResultToServletOS(eventuallyResultWithError)
    }

    // TODO #321 delete?
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
