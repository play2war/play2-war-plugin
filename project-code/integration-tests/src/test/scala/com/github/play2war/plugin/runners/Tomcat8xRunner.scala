package com.github.play2war.plugin.runners

import com.github.play2war.plugin.it._
import java.io.File

/**
 * Starts tomcat7
 */
object Tomcat8xRunner extends App {

  val tomcat8CargoContainer = new CargoContainerManager with Servlet31Container with Java7 {
    val tomcatVersion = "8.0.0-RC5"
    override def containerUrl = s"http://archive.apache.org/dist/tomcat/tomcat-8/v$tomcatVersion/bin/apache-tomcat-$tomcatVersion.tar.gz"
    override def containerName = "tomcat8x"
  }

  val servlet31SampleWarPath = new File("../sample/servlet31/target", "a-play2war-sample-servlet31-1.0-SNAPSHOT.war").getAbsolutePath

  tomcat8CargoContainer.startContainer(servlet31SampleWarPath, stopOnExit = true)
}
