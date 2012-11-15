package play.core.server.websocket.tomcat

import org.apache.catalina.websocket.StreamInbound
import org.apache.catalina.websocket.WebSocketServlet

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServletRequest
import play.core.server.servlet.Play2WarServer

@WebServlet(name = "TomcatWebsocket", urlPatterns = Array { "/echoMessage" }, asyncSupported = true)
class P2WTomcatWebSocketServlet extends WebSocketServlet {

  override def createWebSocketInbound(subProtocol: String, request: HttpServletRequest) = {

    val requestHandler = new TomcatWebSocketRequestHandler(request)
    
    val result = Play2WarServer.handleRequest(requestHandler)

    result.get.asInstanceOf[StreamInbound]
  }

}