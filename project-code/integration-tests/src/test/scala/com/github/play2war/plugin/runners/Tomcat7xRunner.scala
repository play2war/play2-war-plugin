package com.github.play2war.plugin.runners

import java.io.File

import com.github.play2war.plugin.it.{CargoContainerManager, Java8, Servlet30Container}

/**
 * Starts tomcat7
 */
object Tomcat7xRunner extends App {

  val tomcat7CargoContainer = new CargoContainerManager with Servlet30Container with Java8 {
    val tomcatVersion = "7.0.62"
    override def containerUrl = "http://archive.apache.org/dist/tomcat/tomcat-7/v" + tomcatVersion + "/bin/apache-tomcat-" + tomcatVersion + ".tar.gz"
    override def containerName = "tomcat7x"
  }

  val servlet30SampleWarPath = new File("../sample/servlet30/target", "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  tomcat7CargoContainer.startContainer(servlet30SampleWarPath, stopOnExit = true)
}
