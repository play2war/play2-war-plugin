package com.github.play2.warplugin

import sbt._
import sbt.Keys._
import sbt.CommandSupport.{ ClearOnFailure, FailureWall }
import sbt.complete.Parser
import Parser._
import sbt.Cache.seqFormat
import sbinary.DefaultProtocol.StringFormat
import play.api._
import play.core._
import play.utils.Colors
import sbt.PlayKeys._
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import java.lang.{ ProcessBuilder => JProcessBuilder }
import java.util.jar.Manifest
import java.io.{ File, ByteArrayInputStream }

trait Play2WarCommands extends sbt.PlayCommands with sbt.PlayReloader {

  val warTask = (baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, normalizedName, version, streams) map { (root, packaged, dependencies, target, id, version, s) =>

    import sbt.NameFilter._

    val warDir = target
    val packageName = id + "-" + version
    val war = warDir / (packageName + ".war")
    
    s.log.info("Packaging " + war.getCanonicalPath + " ...")

    IO.createDirectory(warDir)

    val libs = {
      dependencies.filterNot(_.data.name.contains("servlet")).filter(_.data.ext == "jar").map { dependency =>
        val filename = for {
          module <- dependency.metadata.get(AttributeKey[ModuleID]("module-id"))
          artifact <- dependency.metadata.get(AttributeKey[Artifact]("artifact"))
        } yield {
          // groupId.artifactId-version[-classifier].extension
          module.organization + "." + module.name + "-" + module.revision + artifact.classifier.map("-" + _).getOrElse("") + "." + artifact.extension
        }
        val path = ("WEB-INF/lib/" + filename.getOrElse(dependency.data.getName))
        dependency.data -> path
      } ++ packaged.map(jar => jar -> ("WEB-INF/lib/" + jar.getName))
    }

    val webxml = warDir / "web.xml"

    //    if (!webxml.exists) {
    //      IO.write(webxml,
    //        """<?xml version="1.0" ?>
    //<web-app xmlns="http://java.sun.com/xml/ns/j2ee"
    //        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    //        xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
    //        version="3.0">
    //  
    //  <display-name>Play! (%APPLICATION_NAME%)</display-name>
    //  
    //  <!--context-param>
    //    <param-name>play.id</param-name>
    //    <param-value>%PLAY_ID%</param-value>
    //  </context-param>
    //  
    //  <listener>
    //      <listener-class>play.core.server.servlet.PlayServletWrapper</listener-class>
    //  </listener>
    //  
    //  <servlet>
    //    <servlet-name>play</servlet-name>
    //    <servlet-class>play.core.server.servlet.PlayServletWrapper</servlet-class>	
    //  </servlet>
    //    	    
    //  <servlet-mapping>
    //    <servlet-name>play</servlet-name>
    //    <url-pattern>/</url-pattern>
    //  </servlet-mapping-->
    //
    //</web-app>
    //""" /* */ )
    //    }
    //
    //    val webxmlFile = Seq(webxml -> ("WEB-INF/web.xml"))

    val config = Option(System.getProperty("config.file"))

    val productionConfig = target / "application.conf"

    val prodApplicationConf = config.map { location =>

      IO.copyFile(new File(location), productionConfig)
      Seq(productionConfig -> ("WEB-INF/classes/application.conf"))
    }.getOrElse(Nil)

    val manifest = new Manifest(
      new ByteArrayInputStream((
        "Manifest-Version: 1.0\n").getBytes))

    IO.jar(libs ++ prodApplicationConf /*++ webxmlFile*/ , war, manifest)
    IO.delete(productionConfig)

    s.log.info("Done packaging.")

    war
  }
}
