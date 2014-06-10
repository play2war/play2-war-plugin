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

import scala.util.control.NonFatal

import javax.servlet.ServletContext
import play.api.Application
import play.api.Configuration
import play.api.Logger
import play.api.Mode
import play.api.Play
import play.api.WithDefaultConfiguration
import play.api.WithDefaultGlobal
import play.api.WithDefaultPlugins
import play.core.ApplicationProvider
import play.core.server.Server
import play.core.server.ServerWithStop
import scala.util.{Try, Success}

object Play2WarServer {

  var playServer: Option[Play2WarServer] = None

  def apply(contextPath: Option[String] = None) = {

    playServer = Option(new Play2WarServer(new WarApplication(Mode.Prod, contextPath)))

  }

  Logger.configure(Map.empty, Map.empty, Mode.Prod)

  lazy val configuration = Play.current.configuration

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

private[servlet] class Play2WarServer(appProvider: WarApplication) extends Server with ServerWithStop {

  private val requestIDs = new java.util.concurrent.atomic.AtomicLong(0)

  def mode = appProvider.mode

  def applicationProvider = appProvider

  // This isn't currently used for anything except local dev mode, so just stub this out for now
  lazy val mainAddress = ???

  def newRequestId = requestIDs.incrementAndGet

  override def stop() = {
    Logger("play").info("Stopping play server...")

    try {
      Play.stop()
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

private[servlet] class WarApplication(val mode: Mode.Mode, contextPath: Option[String]) extends ApplicationProvider {

  val applicationPath = Option(System.getProperty("user.home")).fold(new File("")){ new File(_) }

  val application = new DefaultWarApplication(applicationPath, mode, contextPath)

  // Because of https://play.lighthouseapp.com/projects/82401-play-20/tickets/275, reconfigure Logger
  // without substitutions
  Logger.configure(Map("application.home" -> path.getAbsolutePath), Map.empty,
    mode)

  Play.start(application)

  def get: Try[Application] = Success(application)
  def path = applicationPath
}

private[servlet] class DefaultWarApplication(
  override val path: File,
  override val mode: Mode.Mode,
  private val contextPath: Option[String]
) extends Application with WithDefaultConfiguration with WithDefaultGlobal with WithDefaultPlugins {

  private lazy val warConfiguration = contextPath.filterNot(_.isEmpty)
                                        .map(cp => cp + (if (cp.endsWith("/")) "" else "/"))
                                        .fold(Configuration.empty) { cp =>
                                          Logger("play").info(s"Force Play 'application.context' to '$cp'")
                                          Configuration.from(Map("application.context" -> cp))
                                        } ++ super.configuration

  override def classloader = Thread.currentThread.getContextClassLoader
  override def sources = None
  override def configuration = warConfiguration
}