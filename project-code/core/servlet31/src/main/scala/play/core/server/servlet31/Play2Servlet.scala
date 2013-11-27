package play.core.server.servlet31

import javax.servlet.annotation.{WebListener, WebServlet}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import javax.servlet._
import java.util.concurrent.{LinkedBlockingQueue, LinkedBlockingDeque}
import java.util
import play.api.Logger


@WebServlet(name = "Play", urlPatterns = Array { "/" }, asyncSupported = true)
@WebListener
class Play2Servlet extends HttpServlet {

  override def service(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {

    val async = servletRequest.startAsync()
    async.addListener(new AsyncListener {
      override def onComplete(event: AsyncEvent) {
        event.getSuppliedResponse.getOutputStream.print("Complete")
      }

      override def onError(event: AsyncEvent) {
        System.out.println(event.getThrowable)
      }


      override def onStartAsync(event: AsyncEvent) {

      }

      override def onTimeout(event: AsyncEvent) {
        Logger("play").error("Timeout")
      }
    })

    val servletInputStream = servletRequest.getInputStream
    val readListener = new ReadListenerImpl(servletInputStream, servletResponse, async)
    servletInputStream.setReadListener(readListener)
  }
}

class ReadListenerImpl(val input: ServletInputStream, val response: HttpServletResponse, val asyncContext: AsyncContext) extends ReadListener {

  private val queue: util.Queue[String] = new LinkedBlockingQueue[String]()

  override def onDataAvailable() {
    val sb = new StringBuilder
    val b = new Array[Byte](1024)
    var continue = input.isReady
    while (continue) {
      val len = input.read(b)
      if (len == -1) {
        continue = false
      } else {
        val data = new String(b, 0, len)
        sb.append(data)
        continue = input.isReady
      }
    }
    queue.add(sb.toString())
  }

  override def onAllDataRead() {
    val output = response.getOutputStream
    val writeListener = new WriteListenerImpl(output, queue, asyncContext)
    output.setWriteListener(writeListener)
  }

  override def onError(t: Throwable) {
    asyncContext.complete()
    Logger("play").error("Error while reading input from request", t)
  }
}

class WriteListenerImpl(val output: ServletOutputStream, val queue: util.Queue[String], val asyncContext: AsyncContext) extends WriteListener {

  def onWritePossible() = {
    while (queue.peek() != null && output.isReady) {
      val data = queue.poll()
      output.print(data)
    }

    if (queue.peek() == null) {
      asyncContext.complete()
    }
  }

  def onError(t: Throwable) {
    asyncContext.complete()
    Logger("play").error("Error while writing output", t)
  }

}
