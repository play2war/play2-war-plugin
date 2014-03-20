package com.github.play2war.plugin.runners

import com.github.play2war.plugin.it.{Java7, Servlet30Container, CargoContainerManager}
import java.io.File

object Jetty91xRunner extends App {

  val cargoContainer = new CargoContainerManager with Servlet30Container with Java7 {
    override def containerUrl = "http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.1.3.v20140225/jetty-distribution-9.1.3.v20140225.tar.gz"
    override def containerName = "jetty9x"
  }

  val servlet31SampleWarPath = new File("../sample/servlet31/target", "a-play2war-sample-servlet31-1.0-SNAPSHOT.war").getAbsolutePath

  cargoContainer.startContainer(servlet31SampleWarPath, stopOnExit = true)
}
