package com.github.play2war.plugin.it

import java.net.URL
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers._
import org.scalatest._
import org.codehaus.cargo.container.InstalledLocalContainer
import org.codehaus.cargo.container.installer.ZipURLInstaller
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory
import org.codehaus.cargo.container.ContainerType
import org.codehaus.cargo.container.configuration.ConfigurationType
import org.codehaus.cargo.generic.DefaultContainerFactory
import org.codehaus.cargo.container.configuration.LocalConfiguration
import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import com.gargoylesoftware.htmlunit.util._
import org.codehaus.cargo.container.deployable.WAR
import org.codehaus.cargo.container.property._
import org.codehaus.cargo.util.log._
import scala.collection.immutable.{ Page => _, _ }
import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import java.io.File

trait ServletContainer {

  protected val WAR_KEY = "war.servlet"

  def keyServletContainer: String

  def keyWarPath: String = WAR_KEY + keyServletContainer

}

trait Servlet30Container extends ServletContainer {
  def keyServletContainer = "30"
}

trait Servlet25Container extends ServletContainer {
  def keyServletContainer = "25"
}

trait CargoContainerManager extends BeforeAndAfterAll {
  self: Suite =>

  def getContainer: InstalledLocalContainer

  def setContainer(container: InstalledLocalContainer): Unit

  def containerUrl: String

  def containerFileNameInCloudbeesCache: Option[String] = None

  def containerName: String

  def context = "/"

  def keyWarPath: String

  abstract override def beforeAll(configMap: Map[String, Any]) {

    val warPath = configMap.get(keyWarPath).getOrElse(Nil)

    println("WAR file to deploy: " + warPath)

    val containerUrlToDownload: String = containerFileNameInCloudbeesCache.flatMap { c =>
      val path = "file:///private/play-war/cargo-containers/" + c
      if (new File(path).exists) {
        println("Local container found: " + path)
        Option(path)
      } else {
        println("Local container not found: " + path)
        None
      }
    }.getOrElse(containerUrl)

    println("Download container " + containerName + " from " + containerUrlToDownload + " ...")
    val installer = new ZipURLInstaller(new URL(containerUrlToDownload))
    println("Download container done")

    println("Install container ...")
    installer.install
    println("Install container done")

    val configuration: LocalConfiguration = new DefaultConfigurationFactory().createConfiguration(
      containerName, ContainerType.INSTALLED, ConfigurationType.STANDALONE).asInstanceOf[LocalConfiguration]

    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.MEDIUM.getLevel);

    val container =
      new DefaultContainerFactory().createContainer(
        containerName, ContainerType.INSTALLED, configuration).asInstanceOf[InstalledLocalContainer]

    println("Configure container")
    container.setHome(installer.getHome)
    container.setLogger(new SimpleLogger)

    val war = new WAR(warPath.toString)
    war.setContext(context)
    configuration.addDeployable(war)

    println("Start the container " + containerName)
    setContainer(container)
    container.start
  }

  abstract override def afterAll {
    println("Stop the container")
    Some(getContainer).map {
      _.stop
    }
  }
}