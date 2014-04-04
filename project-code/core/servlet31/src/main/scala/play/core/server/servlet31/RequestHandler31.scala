package play.core.server.servlet31

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import play.core.server.servlet.{RichHttpServletResponse, RichHttpServletRequest, Play2GenericServletRequestHandler}
import java.io.{OutputStream, InputStream}
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.{ServletInputStream, ReadListener, AsyncEvent}
import play.api.Logger
import play.api.libs.iteratee._
import scala.concurrent.{Future, Promise}
import play.api.libs.iteratee.Input.El
import play.api.mvc.SimpleResult

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
            nextStep.run.map { a =>
              Logger("play.war.servlet31").trace("extract result from nextStep")
              result.success(a)
            }(exContext)
          }
          nextStep
        }(exContext)

      }

      def onAllDataRead() {
        Logger("play.war.servlet31").trace("onAllDataRead")
        if (!onDataAvailableCalled) {
          // some containers, like Jetty, call directly onAllDataRead without calling onDataAvailable
          // when no data should be consumed
          iteratee.run.map { a =>
            Logger("play.war.servlet31").trace("onAllDataRead: extract result from iteratee")
            result.success(a)
          }(exContext)
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

  override protected def feedBodyParser(bodyParser: Iteratee[Array[Byte], SimpleResult]): Future[SimpleResult] = {
    val servletInputStream = servletRequest.getInputStream
    readServletRequest(servletInputStream, bodyParser)
  }
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
