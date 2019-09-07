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

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.net.URLDecoder
import java.util.Collections

import akka.NotUsed
import akka.stream.impl.fusing.FlattenMerge
import akka.stream.{Graph, _}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Partition, RunnableGraph, Sink, Source, StreamConverters, SubFlow}
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

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.control.Exception
import scala.util.{Failure, Success, Try}

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

  protected def getServletCookie(pCookie: Cookie): ServletCookie

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
  protected def convertRequestBody(): Option[Source[ByteString, Future[_]]] = {
    getHttpRequest().getRichInputStream.map { is ⇒
      StreamConverters.fromInputStream(() => is)
    }
  }

  /** Create the sink for the response body */
  protected def convertResponseBody(): Option[Sink[ByteString, Future[_]]] = {
    getHttpResponse().getRichOutputStream.map { os ⇒
      StreamConverters.fromOutputStream(() => os)
    }
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

  protected def setCookies(newCookies: Seq[Cookie], httpResponse: HttpServletResponse): Unit = {
    newCookies.foreach { pCookie =>
      httpResponse.addCookie(getServletCookie(pCookie))
    }
  }

  protected def setFlash(newFlash: Option[Flash], httpResponse: HttpServletResponse): Unit = {
    newFlash.foreach { pFlash =>
      httpResponse.addCookie(getServletCookie(Flash.encodeAsCookie(pFlash)))
    }
  }

  /**
   * default implementation to push a play result to the servlet output stream
   * @param futureResult the result of the play action
   */
  protected def pushPlayResultToServletOS(futureResult: Future[Result])(implicit mat: Materializer): Unit = {

    futureResult.map { result =>
      getHttpResponse().getHttpServletResponse.foreach { httpResponse =>

        val status = result.header.status
        val headers = result.header.headers
        val body: HttpEntity = result.body

        logger.trace("Sending simple result: " + result)

        httpResponse.setStatus(status)

        setHeaders(headers, httpResponse)
        setCookies(result.newCookies, httpResponse)
        setFlash(result.newFlash, httpResponse)
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
          val sink: Sink[ByteString, Future[_]] = convertResponseBody().getOrElse(Sink.ignore)
          val graph: RunnableGraph[Future[_]] = source.toMat(sink)(Keep.right)
          graph.run().andThen{
            case Success(_) =>
              onHttpResponseComplete()
            case Failure(ex) =>
              logger.debug(ex.toString)
              onHttpResponseComplete()
          }(mat.executionContext)
        } else {
          // No Content-Length header specified, buffer in-memory
          val buffer = new ByteArrayOutputStream
          val sink: Sink[ByteString, Future[ByteArrayOutputStream]] = Sink.fold[ByteArrayOutputStream, ByteString](buffer)((b, e) => {
            buffer.write(e.toArray); b
          })

          val graph: RunnableGraph[Future[ByteArrayOutputStream]] = source.toMat(sink)(Keep.right)
          graph.run().andThen {
            case Success(_) =>
              logger.trace(s"Buffer size to send: ${buffer.size()}")
              getHttpResponse.getHttpServletResponse.map { response =>
                // set the content length ourselves
                response.setContentLength(buffer.size)
                response.setBufferSize(buffer.size)
                val os = response.getOutputStream
                os.flush()
                buffer.writeTo(os)
                os.flush()
              }
              onHttpResponseComplete()
            case Failure(ex) =>
              logger.debug(ex.toString)
              onHttpResponseComplete()
          }(mat.executionContext)
        }

      } // end match foreach

    }(mat.executionContext)
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

    def bakeCookies(result: Result): Result = {
      val requestHasFlash = requestHeader.attrs.get(RequestAttrKey.Flash) match {
        case None =>
          // The request didn't have a flash object in it, either because we
          // used a custom RequestFactory which didn't install the flash object
          // or because there was an error in request processing which caused
          // us to bypass the application's RequestFactory. In this case we
          // can assume that there is no flash object we need to clear.
          false
        case Some(flashCell) =>
          // The request had a flash object and it was non-empty, so the flash
          // cookie value may need to be cleared.
          !flashCell.value.isEmpty
      }
      result.bakeCookies( server.applicationProvider.cookieHeaderEncoding,
                          server.applicationProvider.sessionBaker,
                          server.applicationProvider.flashBaker,
                          requestHasFlash)
    }

    def cleanFlashCookie(result: Result): Result = {
      val header = result.header

      val flashCookie = {
        header.headers.get(HeaderNames.SET_COOKIE)
          .map(Cookies.decodeSetCookieHeader)
          .flatMap(_.find(_.name == server.applicationProvider.flashBaker.COOKIE_NAME)).orElse {
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
        handleAction(action, requestHeader, Some(app))

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

      val body: Option[Source[ByteString, Future[_]]] = convertRequestBody()
      val bodyParser: Accumulator[ByteString, Result] = action(requestHeader)
      val resultFuture: Future[Result] = body match {
        case None =>
          bodyParser.run()
        case Some(source) =>
          bodyParser.run(source)
      }

      val httpErrorHandler = app.fold[HttpErrorHandler](DefaultHttpErrorHandler)(_.errorHandler)

      val eventuallyResultWithError: Future[Result] = resultFuture.recoverWith {
        case error =>
          logger.error("Cannot invoke the action", error)
          httpErrorHandler.onServerError(requestHeader, error)
      }(mat.executionContext)
        .map(bakeCookies)(mat.executionContext)
        .map(cleanFlashCookie)(mat.executionContext)
      pushPlayResultToServletOS(eventuallyResultWithError)
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
