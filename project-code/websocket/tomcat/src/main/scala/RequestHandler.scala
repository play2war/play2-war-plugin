package play.core.server.websocket.tomcat

import java.nio.ByteBuffer
import java.nio.CharBuffer
import org.apache.catalina.websocket.MessageInbound
import javax.servlet.http.HttpServletRequest
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.mvc.WebSocket
import play.core.server.servlet30.Play2Servlet30RequestHandler
import org.apache.catalina.websocket.WsOutbound

class TomcatWebSocketRequestHandler(servletRequest: HttpServletRequest) extends Play2Servlet30RequestHandler(servletRequest) {

  override protected def onWebsocket(ws: WebSocket[Any], f: RequestHeader => (Enumerator[Any], Iteratee[Any, Unit]) => Unit, app: play.api.Application): Option[AnyRef] = {
    Logger("play").debug("onWebSocket - ws: " + ws + " - f: " + f)

    Option(new P2WTomcatMessageInbound)
  }

}

class P2WTomcatMessageInbound extends MessageInbound {

  override protected def onOpen(outbound: WsOutbound) = {
    Logger("play").debug("onOpen")
  }

  override def onBinaryMessage(message: ByteBuffer) = {
    Logger("play").debug("onBinaryMessage")
  }

  override def onTextMessage(message: CharBuffer) = {
    Logger("play").debug("onTextMessage")
  }

  override def onClose(status: Int) = {
    Logger("play").debug("onClose")
  }

}