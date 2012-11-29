package play.core.server.servlet

import java.io.File
import java.util.logging.Handler
import java.net.InetSocketAddress

import scala.Option.apply
import scala.Predef.Map.apply
import scala.Right.apply

import javax.servlet.ServletContext
import play.api.Configuration
import play.api.DefaultApplication
import play.api.Logger
import play.api.Logger.apply
import play.api.Mode
import play.api.Play
import play.core.ApplicationProvider
import play.core.server.Server
import play.core.server.ServerWithStop

object Play2WarServer {

  // See https://github.com/dlecan/play2-war-plugin/issues/54
  // Store all handlers before Play Logger.configure(...)
  private val julHandlers: Option[Array[Handler]] = Option(java.util.logging.Logger.getLogger("")).map { root =>
    root.getHandlers
  }

  Logger.configure(Map.empty, Map.empty, Mode.Prod)

  private val classLoader = getClass.getClassLoader;

  private val application = new WarApplication(classLoader, Mode.Prod, julHandlers)

  val configuration = application.get.right.map { _.configuration }.right.getOrElse(Configuration.empty)

  val playServer = new Play2WarServer(application)

  private var started = true

  def stop(sc: ServletContext) = {
    synchronized {
      if (started) {
        playServer.stop()
        sc.log("Play server stopped")
        started = false
      }
    }
  }

  def handleRequest(requestHandler: RequestHandler) = {

    requestHandler(playServer)

  }
}

private[servlet] class Play2WarServer(appProvider: WarApplication) extends Server with ServerWithStop {

  def mode = appProvider.mode

  def applicationProvider = appProvider

  // This isn't currently used for anything except local dev mode, so just stub this out for now
  lazy val mainAddress = ???

  override def stop() = {
    Logger("play").info("Stopping play server...")

    try {
      Play.stop()
    } catch {
      case e: Throwable => Logger("play").error("Error while stopping the application", e)
    }

    try {
      super.stop()
    } catch {
      case e: Throwable => Logger("play").error("Error while stopping akka", e)
    }
  }
}

private[servlet]class WarApplication(val classLoader: ClassLoader, val mode: Mode.Mode, val julHandlers: Option[Array[Handler]]) extends ApplicationProvider {

  val applicationPath = Option(System.getProperty("user.home")).map(new File(_)).getOrElse(new File(""))

  val application = new DefaultApplication(applicationPath, classLoader, None, mode)

  // Because of https://play.lighthouseapp.com/projects/82401-play-20/tickets/275, reconfigure Logger
  // without substitutions
  Logger.configure(Map("application.home" -> path.getAbsolutePath), Map.empty,
    mode)

  // Restore handlers after Play logger initialization
  Option(java.util.logging.Logger.getLogger("")).map { root =>
    julHandlers.map { handlers =>
      handlers.foreach(root.addHandler(_))
    }
  }

  Play.start(application)

  def get = Right(application)
  def path = applicationPath
}
