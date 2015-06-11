package com.github.play2war.plugin.runners

import java.io.File

import com.github.play2war.plugin.it.{Java8, CargoContainerManager, Servlet30Container}

/**
  * Starts jetty9
  */
object Jetty90xRunner extends App {

   val cargoContainer = new CargoContainerManager with Servlet30Container with Java8 {
     override def containerUrl = "http://central.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.0.6.v20130930/jetty-distribution-9.0.6.v20130930.tar.gz"
     override def containerName = "jetty9x"
   }

   val servlet30SampleWarPath = new File("../sample/servlet30/target", "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  cargoContainer.startContainer(servlet30SampleWarPath, stopOnExit = true)
 }

