package play.core.server.websocket.tomcat

import java.nio.ByteBuffer
import java.nio.CharBuffer
import javax.servlet.http.HttpServletRequest
import org.apache.catalina.websocket.MessageInbound
import org.apache.catalina.websocket.StreamInbound
import org.apache.catalina.websocket.WebSocketServlet
import javax.servlet.annotation.WebServlet
import play.core.server.servlet30.Play2Servlet30RequestHandler
import play.core.server.servlet.Play2WarServer

@WebServlet(name = "TomcatWebsocket", urlPatterns = Array { "/websocket" }, asyncSupported = true)
class P2WTomcatWebSocketServlet extends WebSocketServlet {

  override def createWebSocketInbound(subProtocol: String, request: HttpServletRequest) = {

    val requestHandler = new Play2Servlet30RequestHandler(request)
    
//    Play2WarServer.handleRequest(requestHandler)

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