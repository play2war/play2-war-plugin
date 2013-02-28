package com.github.play2war.plugin

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.jar.Manifest

import scala.collection.immutable.Stream.consWrapper

import com.github.play2war.plugin.Play2WarKeys.servletVersion
import com.github.play2war.plugin.Play2WarKeys.webappResource
import com.github.play2war.plugin.Play2WarKeys.targetName

import sbt.ConfigKey.configurationToKey
import sbt.Keys.TaskStreams
import sbt.Keys.dependencyClasspath
import sbt.Keys.normalizedName
import sbt.Keys.streams
import sbt.Keys.target
import sbt.Keys.version
import sbt.Runtime
import sbt.richFile
import sbt.Artifact
import sbt.AttributeKey
import sbt.IO
import sbt.ModuleID
import sbt.Path

trait Play2WarCommands extends sbt.PlayCommands with sbt.PlayReloader with sbt.PlayPositionMapper {

  val manifestRegex = """(?i).*META-INF/MANIFEST.MF"""
    
    def getFiles(root: File, skipHidden: Boolean = false): Stream[File] =  
      if (!root.exists || (skipHidden && root.isHidden) || 
          manifestRegex.r.pattern.matcher(root.getAbsolutePath()).matches()) Stream.empty  
      else root #:: ( 
        root.listFiles match { 
          case null => Stream.empty 
          case files => files.toStream.flatMap(getFiles(_, skipHidden)) 
      })

  val warTask = (playPackageEverything, dependencyClasspath in Runtime, target, normalizedName, version, webappResource, streams, servletVersion, targetName) map {
    (packaged, dependencies, target, id, version, webappResource, s, servletVersion, targetName) =>

      s.log.info("Build WAR package for servlet container: " + servletVersion)

      if(dependencies.exists(_.data.name.contains("play2-war-core-common"))) {
        s.log.debug("play2-war-core-common found in dependencies!")
      } else {
        s.log.error("play2-war-core-common not found in dependencies!")
        throw new IllegalArgumentException("play2-war-core-common not found in dependencies!")
      }
    
      val warDir = target
      val packageName = targetName.getOrElse(id + "-" + version)
      val war = warDir / (packageName + ".war")
      val manifestString = "Manifest-Version: 1.0\n"
        
      s.log.info("Packaging " + war.getCanonicalPath + " ...")

      IO.createDirectory(warDir)

      val libs = {
        // Much better: filter by scope (exclude 'provided')
        dependencies.filterNot(_.data.name.contains("servlet-api")).filter(_.data.ext == "jar").map { dependency =>
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
      
      libs.foreach { l => 
        s.log.debug("Embedding dependency " + l._1 + " -> " + l._2)
      }

      val webxmlFolder = webappResource / "WEB-INF"
      val webxml = webxmlFolder / "web.xml"
      
      // Web.xml generation
      servletVersion match {
        case "2.5" => {
          
            if (webxml.exists) {
              s.log.info("WEB-INF/web.xml found.")
            } else {
              s.log.info("WEB-INF/web.xml not found, generate it in " + webxmlFolder)
              IO.write(webxml,
                """<?xml version="1.0" ?>
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
        version="2.5">
  
  <display-name>Play! """ + id + """</display-name>
  
  <listener>
      <listener-class>play.core.server.servlet25.Play2Servlet</listener-class>
  </listener>
  
  <servlet>
    <servlet-name>play</servlet-name>
    <servlet-class>play.core.server.servlet25.Play2Servlet</servlet-class>	
  </servlet>
    	    
  <servlet-mapping>
    <servlet-name>play</servlet-name>
    <url-pattern>/</url-pattern>
  </servlet-mapping>

</web-app>
""" /* */ )
            }
            
        }
        
        case "3.0" => //handleWebXmlFileOnServlet30(webxml, s)
        
        case unknown => {
            s.log.warn("Unknown servlet container version: " + unknown + ". Force default 3.0 version")
            handleWebXmlFileOnServlet30(webxml, s)
        }
      }

      // Webapp resources
      s.log.debug("Webapp resources directory: " + webappResource.getAbsolutePath)
      
      val filesToInclude = getFiles(webappResource).filter(f => f.isFile)
      
      val additionnalResources = filesToInclude.map { f =>
        f -> Path.relativizeFile(webappResource, f).get.getPath
      }
          
      additionnalResources.foreach { r => 
        s.log.debug("Embedding " + r._1 + " -> /" + r._2)
      }

      val metaInfFolder = webappResource / "META-INF"
      val manifest  = if (metaInfFolder.exists()) {
        val option = metaInfFolder.listFiles.find(f => 
          manifestRegex.r.pattern.matcher(f.getAbsolutePath()).matches())
        if (option.isDefined){
          new Manifest(new FileInputStream(option.get))
        }
        else {
      	  new Manifest(new ByteArrayInputStream(manifestString.getBytes))            
        }
      }
      else {
        new Manifest(new ByteArrayInputStream(manifestString.getBytes))
      } 
      
      // Package final jar
      val jarContent = libs ++ additionnalResources

      IO.jar(jarContent, war, manifest)

      s.log.info("Packaging done.")

      war
  }
  
  def handleWebXmlFileOnServlet30(webxml: File, s: TaskStreams) = {
    if (webxml.exists) {
      s.log.warn("WEB-INF/web.xml found! As WAR package will be built for servlet 3.0 containers, check if this web.xml file is compatible with.")
    }
  }
}