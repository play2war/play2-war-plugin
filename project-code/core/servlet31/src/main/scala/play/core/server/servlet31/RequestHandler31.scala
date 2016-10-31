package play.core.server.servlet31

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import play.core.server.servlet.{RichHttpServletResponse, RichHttpServletRequest, Play2GenericServletRequestHandler}
import java.io.{OutputStream, InputStream}
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet._
import play.api.Logger
import play.api.libs.iteratee._
import scala.concurrent.{Future, Promise}
import play.api.mvc.{Results, Result}
import play.api.libs.iteratee.Input.El
import play.api.http.{HttpProtocol, HeaderNames}

class Play2Servlet31RequestHandler(servletRequest: HttpServletRequest)
  extends Play2GenericServletRequestHandler(servletRequest, None)
  with Helpers {

  val asyncListener = new AsyncListener(servletRequest.toString)

  val asyncContext = servletRequest.startAsync
  asyncContext.setTimeout(play.core.server.servlet31.Play2Servlet.asyncTimeout)

  protected override def onFinishService() = {
    // Nothing to do
  }

  protected override def onHttpResponseComplete() = {
    asyncContext.complete()
  }

  protected override def getHttpRequest(): RichHttpServletRequest = {
    new RichHttpServletRequest {
      def getRichInputStream(): Option[InputStream] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getRequest.getInputStream)
        } else {
          None
        }
      }
    }
  }

  protected override def getHttpResponse(): RichHttpServletResponse = {
    new RichHttpServletResponse {
      def getRichOutputStream: Option[OutputStream] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getResponse.getOutputStream)
        } else {
          None
        }
      }

      def getHttpServletResponse: Option[HttpServletResponse] = {
        if (asyncContextAvailable(asyncListener)) {
          Option(asyncContext.getResponse.asInstanceOf[HttpServletResponse])
        } else {
          None
        }
      }
    }
  }

  private def asyncContextAvailable(asyncListener: AsyncListener) = {
    !asyncListener.withError.get && !asyncListener.withTimeout.get
  }

  private def readServletRequest[A](servletInputStream: ServletInputStream, consumer: => Iteratee[Array[Byte], A]): Future[A] = {
    val exContext = play.core.Execution.internalContext

    val result = Promise[A]()
    @volatile var onDataAvailableCalled = false
    var iteratee: Iteratee[Array[Byte], A] = consumer

    val readListener = new ReadListener {

      // re-use buffer
      private[this] val buffer = new Array[Byte](Play2Servlet.internalUploadBufferSide)

      private[this] def consumeBody(body: ServletInputStream, buffer: Array[Byte]): Array[Byte] = {
        val output = new java.io.ByteArrayOutputStream()
        var continue = body.isReady
        while (continue) {
          val length = body.read(buffer)
          if (length == -1) {
            continue = false
          } else {
            output.write(buffer, 0, length)
            continue = body.isReady
          }
        }
        output.toByteArray
      }

      def onDataAvailable() {
        Logger("play.war.servlet31").trace(s"onDataAvailable")
        onDataAvailableCalled = true
        iteratee = iteratee.pureFlatFold { step =>

          // consume the http body in any case
          val chunk = consumeBody(servletInputStream, buffer)
          Logger("play.war.servlet31").trace(s"consumes ${chunk.length} bytes")

          val nextStep = step match {
            case Step.Cont(k) => k(El(chunk))
            case other => other.it
          }

          if (servletInputStream.isFinished) {
            Logger("play.war.servlet31").trace("will extract result from nextStep")
            result.completeWith(nextStep.run.andThen { case _ =>
              Logger("play.war.servlet31").trace("extract result from nextStep")
            }(exContext))
          }
          nextStep
        }(exContext)

      }

      def onAllDataRead() {
        Logger("play.war.servlet31").trace("onAllDataRead")
        if (!onDataAvailableCalled) {
          // some containers, like Jetty, call directly onAllDataRead without calling onDataAvailable
          // when no data should be consumed
          result.completeWith(iteratee.run.andThen { case _ =>
            Logger("play.war.servlet31").trace("onAllDataRead: extract result from iteratee")
          }(exContext))
        }
      }

      def onError(t: Throwable) {
        Logger("play.war.servlet31").trace("onError", t)
        result.failure(t)
      }
    }

    servletInputStream.setReadListener(readListener)
    result.future
  }

  override protected def feedBodyParser(bodyParser: Iteratee[Array[Byte], Result]): Future[Result] = {
    val servletInputStream = servletRequest.getInputStream
    readServletRequest(servletInputStream, bodyParser)
  }

  /**
   * push the result of a play action asynchronously to a servlet output stream
   * @param httpResponse servlet response
   * @param out servlet output stream
   * @param futureResult result of a play action
   * @param cleanup clean up callback
   */
//  private class ResultWriteListener(
//      val httpResponse: HttpServletResponse,
//      val out: ServletOutputStream,
//      val futureResult: Future[Result]) extends WriteListener {
//
//    // the promise is completed when a write to the servlet IO is possible
//    @volatile var iterateeP = Promise[Iteratee[Array[Byte], Unit]]()
//    val futureIteratee = iterateeP.future
//
//    @volatile var chunked = true
//    Logger("play.war.servlet31").trace("set write listener")
//
//    private def step(): Iteratee[Array[Byte], Unit] = {
//      Logger("play.war.servlet31").trace(s"step")
//      iterateeP = Promise[Iteratee[Array[Byte], Unit]]()
//
//      Cont[Array[Byte], Unit] {
//        case Input.EOF =>
//          Logger("play.war.servlet31").trace(s"EOF, finished!")
//          if (out.isReady) {
//            out.flush()
//          }
//          onHttpResponseComplete()
//          cleanup()
//          Done[Array[Byte], Unit](Unit)
//
//        case Input.Empty =>
//          Logger("play.war.servlet31").trace(s"empty, just continue")
//          step()
//
//        case Input.El(buffer) =>
//          out.write(buffer)
//          if (chunked && out.isReady) {
//            // flush to send chunked content, like comet message
//            out.flush()
//          }
//          Logger("play.war.servlet31").trace(s"send ${buffer.length} bytes. out.isReady=${out.isReady}")
//          if (out.isReady) {
//            // can immediately push the next bytes
//            step()
//          } else {
//            // wait for next onWritePossible
//            Iteratee.flatten(iterateeP.future)
//          }
//      }
//    }
//
//    override def onWritePossible(): Unit = {
//      Logger("play.war.servlet31").trace("onWritePossible - begin")
//      if (iterateeP.isCompleted) {
//        throw new Exception("race condition: the servlet container should not call onWritePossible() when the iteratee is completed. Please report.")
//      }
//
//      // write is possible, let's use it
//      iterateeP.success(step())
//    }
//
//    override def onError(t: Throwable): Unit = {
//      Logger("play.war.servlet31").error("error while writing result to servlet output stream", t)
//      onHttpResponseComplete()
//      cleanup()
//    }
//
//    import play.core.Execution.Implicits.internalContext
//
//    futureIteratee.foreach { bodyIteratee =>
//      futureResult.foreach { result =>
//        val status = result.header.status
//        val headers = result.header.headers
//
//        Logger("play.war.servlet31").trace("Sending simple result: " + result)
//
//        httpResponse.setStatus(status)
//
//        setHeaders(headers, httpResponse)
//
//        chunked = headers.exists { case (key, value) => key.equalsIgnoreCase(HeaderNames.TRANSFER_ENCODING) && value == HttpProtocol.CHUNKED }
//
//        Logger("play.war.servlet31").trace(s"the body iteratee is ready. chunked=$chunked")
//        if (chunked) {
//          // if the result body is chunked, the chunks are already encoded with metadata in Results.chunk
//          // The problem is that the servlet container adds metadata again, leading the chunks encoded 2 times.
//          // As workaround, we 'dechunk' the body one time before sending it to the servlet container
//          result.body &> Results.dechunk |>>> bodyIteratee
//        } else {
//          result.body |>>> bodyIteratee
//        }
//      }
//    }
//  }

//  override protected def pushPlayResultToServletOS(futureResult: Future[Result]): Future[Unit] = {
//    getHttpResponse().getHttpServletResponse.foreach { httpResponse =>
//
//      val out = httpResponse.getOutputStream.asInstanceOf[ServletOutputStream]
//
//      // tomcat insists that the WriteListener is set on the servlet thread.
//      out.setWriteListener(new ResultWriteListener(httpResponse, out, futureResult))
//    }
//  }
}

private[servlet31] class AsyncListener(val requestId: String) extends javax.servlet.AsyncListener {

  val withError = new AtomicBoolean(false)

  val withTimeout = new AtomicBoolean(false)

  // Need a default constructor for JBoss
  def this() = this("Unknown request id")

  override def onComplete(event: AsyncEvent): Unit = {
    // Logger("play").trace("onComplete: " + requestId)
    // Nothing
  }

  override def onError(event: AsyncEvent): Unit = {
    withError.set(true)
    Logger("play").error("Error asynchronously received for request: " + requestId, event.getThrowable)
  }

  override def onStartAsync(event: AsyncEvent): Unit = {} // Nothing

  override def onTimeout(event: AsyncEvent): Unit = {
    withTimeout.set(true)
    Logger("play").warn("Timeout asynchronously received for request: " + requestId)
  }
}
