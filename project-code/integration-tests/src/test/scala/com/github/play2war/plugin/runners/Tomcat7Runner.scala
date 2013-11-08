package com.github.play2war.plugin.runners

import com.github.play2war.plugin.it.{Java6, Servlet30Container, CargoContainerManager}
import java.io.File

/**
 * Starts tomcat7
 */
object Tomcat7Runner extends App {

  val tomcat7CargoContainer = new CargoContainerManager with Servlet30Container with Java6 {
    val tomcatVersion = "7.0.39"
    override def containerUrl = "http://archive.apache.org/dist/tomcat/tomcat-7/v" + tomcatVersion + "/bin/apache-tomcat-" + tomcatVersion + ".tar.gz"
    override def containerName = "tomcat7x"
    override def containerFileNameInCloudbeesCache = Option("apache-tomcat-7.0.39.zip")
  }

  val servlet30SampleWarPath = new File("../sample/common/target", "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  tomcat7CargoContainer.startContainer(servlet30SampleWarPath)

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() {
      tomcat7CargoContainer.stopContainer
    }
  })
}
