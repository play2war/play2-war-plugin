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
    implicit val internalContext = play.core.Execution.internalContext

    var doneOrError = false
    val result = Promise[A]()
    var iteratee: Iteratee[Array[Byte], A] = consumer

    val readListener = new ReadListener {

      def onDataAvailable() {
        val buffer = new Array[Byte](1024 * 8)
        val output = new java.io.ByteArrayOutputStream()
        var continue = servletInputStream.isReady
        while (continue) {
          val length = servletInputStream.read(buffer)
          if (length == -1) {
            continue = false
          } else {
            output.write(buffer, 0, length)
            continue = servletInputStream.isReady
          }
        }

        if (!doneOrError) {
          iteratee = iteratee.pureFlatFold {
            case Step.Done(a, e) => {
              doneOrError = true
              val it = Done(a, e)
              result.success(a)
              it
            }

            case Step.Cont(k) => {
              k(El(output.toByteArray))
            }

            case Step.Error(e, input) => {
              doneOrError = true
              result.failure(new Exception(e))
              val it = Error(e, input)
              it
            }
          }
        } else {
          iteratee = null
        }
      }

      def onAllDataRead() {
        val maybeIteratee = Option(iteratee)
        Logger("play").trace("onAllDataRead: " + maybeIteratee.isDefined)

        maybeIteratee.map { it =>
          it.run.map(a => result.success(a))
        }
      }

      def onError(t: Throwable) {
        Logger("play").trace("onError", t)
        result.failure(t)
        asyncContext.complete()
      }
    }
    servletInputStream.setReadListener(readListener)
    result.future
  }

  override protected def feedBodyParser(bodyParser:Iteratee[Array[Byte], SimpleResult]): Future[SimpleResult] = {
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
