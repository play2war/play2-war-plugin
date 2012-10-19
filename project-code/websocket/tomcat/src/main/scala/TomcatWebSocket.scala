package play.core.server.websocket.tomcat

import java.nio.ByteBuffer
import java.nio.CharBuffer

import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.websocket.MessageInbound
import org.apache.catalina.websocket.StreamInbound;
import org.apache.catalina.websocket.WebSocketServlet

class P2WTomcatWebSocketServlet extends WebSocketServlet {

  override def createWebSocketInbound(subProtocol: String, request: HttpServletRequest) = {
    new P2WTomcatMessageInbound;
  }

}

class P2WTomcatMessageInbound extends MessageInbound {

  override def onBinaryMessage(message: ByteBuffer) = {
    // TODO Auto-generated method stub

  }

  override def onTextMessage(message: CharBuffer) = {
    // TODO Auto-generated method stub

  }

  override def onClose(status: Int) = {
    // TODO Auto-generated method stub
    super.onClose(status);
  }

}