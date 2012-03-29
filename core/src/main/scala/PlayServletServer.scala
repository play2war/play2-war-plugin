package play.core.server.servlet

import java.io._

import play.core._
import play.core.server._
import play.api._
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Input._
import play.api.libs.concurrent._

import scala.collection.JavaConverters._

class PlayServletServer(appProvider: WarApplication) extends Server with ServerWithStop {

  def mode = appProvider.mode
  
  def applicationProvider = appProvider

  override def stop() = {
    Logger("play").info("Stopping play server...")

    try {
      Play.stop()
    } catch {
      case e => Logger("play").error("Error while stopping the application", e)
    }

    try {
      super.stop()
    } catch {
      case e => Logger("play").error("Error while stopping akka", e)
    }
  }
}

class WarApplication(classLoader: ClassLoader, val mode: Mode.Mode) extends ApplicationProvider {

  val applicationPath = new File("")

  val application = new Application(applicationPath, classLoader, None, mode)

  Play.start(application)

  def get = Right(application)
  def path = applicationPath
}
