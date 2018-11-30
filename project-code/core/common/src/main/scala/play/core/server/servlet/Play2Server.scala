/*
 * Copyright 2013 Damien Lecan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.core.server.servlet

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import akka.stream.Materializer
import javax.servlet.ServletContext
import play.api._
import play.core.ApplicationProvider
import play.core.server.Server

import scala.util.control.NonFatal
import scala.util.{Success, Try}

object Play2WarServer {

  var playServer: Option[Play2WarServer] = None

  def apply(contextPath: Option[String] = None) = {

    playServer = Option(new Play2WarServer(new WarApplication(Mode.Prod, contextPath)))

  }

  val context = ApplicationLoader.createContext(
    new Environment(new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Prod))
//  Logger.configure(context.environment) // TODO #321 what about Logger?

  lazy val configuration = Play.current.configuration // TODO #321 use DI instead of Play.current

  private val started = new AtomicBoolean(true)

  def stop(sc: ServletContext) = {
    synchronized {
      if (started.getAndSet(false)) {
        playServer.foreach(_.stop())
        sc.log("Play server stopped")
      }
    }
  }

  def handleRequest(requestHandler: RequestHandler) = {

    playServer.fold {
      Logger("play").error("Play server as not been initialized. Due to a previous error ?")
    }(requestHandler(_))

  }
}

private[servlet] class Play2WarServer(appProvider: WarApplication) extends Server {

  private val requestIDs = new java.util.concurrent.atomic.AtomicLong(0)

  def mode = appProvider.mode
  def materializer: Materializer = appProvider.application.materializer

  def applicationProvider = appProvider

  // This isn't currently used for anything except local dev mode, so just stub this out for now
  lazy val mainAddress = ???
  override def httpPort: Option[Int] = ???
  override def httpsPort: Option[Int] = ???

  def newRequestId = requestIDs.incrementAndGet

  override def stop() = {
    Logger("play").info("Stopping play server...")

    try {
      Play.stop(appProvider.application)
    } catch {
      case NonFatal(e) => Logger("play").error("Error while stopping the application", e)
    }

    try {
      super.stop()
    } catch {
      case NonFatal(e) => Logger("play").error("Error while stopping akka", e)
    }
  }
}

private[servlet] class WarApplication(val mode: Mode, contextPath: Option[String]) extends ApplicationProvider {

  val applicationPath = Option(System.getProperty("user.home")).fold(new File("")){ new File(_) }

  val application: Application = {
    val environment = Environment(new File("."), Thread.currentThread.getContextClassLoader, mode)
    val initialSettings = contextPath
      .filterNot(_.isEmpty)
      .map(cp => cp + (if (cp.endsWith("/")) "" else "/"))
      .fold(Map.empty[String, AnyRef]) { cp ⇒
        Logger("play").info(s"Force Play 'play.http.context' to '$cp'")
        Map("play.http.context" -> cp)
      }
    val context = ApplicationLoader.createContext(environment, initialSettings = initialSettings)
    // Because of https://play.lighthouseapp.com/projects/82401-play-20/tickets/275, reconfigure Logger
    // without substitutions
//    Logger.configure(context.environment) // TODO #321 what about Logger?

    val loader = ApplicationLoader(context)
    loader.load(context)
  }

  Play.start(application)

  def get: Try[Application] = Success(application)
  def path = applicationPath
}
