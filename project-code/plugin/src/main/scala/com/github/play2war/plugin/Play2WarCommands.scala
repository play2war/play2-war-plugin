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
package com.github.play2war.plugin

import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.jar.Manifest

import scala.collection.immutable.Stream.consWrapper

import com.github.play2war.plugin.Play2WarKeys._

import com.typesafe.sbt.packager.Keys.projectDependencyArtifacts
import sbt.ConfigKey.configurationToKey
import sbt.Keys._
import sbt._

trait Play2WarCommands extends play.PlayCommands with play.PlayReloader with play.PlayPositionMapper {

  val manifestRegex = """(?i).*META-INF/MANIFEST.MF"""

  def getFiles(root: File, skipHidden: Boolean = false): Stream[File] =
    if (!root.exists || (skipHidden && root.isHidden) ||
      manifestRegex.r.pattern.matcher(root.getAbsolutePath()).matches()) {
      Stream.empty
    } else {
      root #:: (
        root.listFiles match {
          case null => Stream.empty
          case files => files.toStream.flatMap(getFiles(_, skipHidden))
        })
    }

  val warTask: Def.Initialize[Task[sbt.File]] = Def.task[sbt.File] {
    val s = streams.value

    // projectDependencyArtifacts value contains the jar of the projects and all sub-projects
    val dependencies = (dependencyClasspath in Runtime).value ++ (projectDependencyArtifacts in Runtime).value
    val unmanagedDependencies = (unmanagedClasspath in Runtime).value
    val id = normalizedName.value
    //val packaged = com.typesafe.sbt.packager.Keys.dist.value
    s.log.info("Build WAR package for servlet container: " + servletVersion.value)

    if (dependencies.exists(_.data.name.contains("play2-war-core-common"))) {
      s.log.debug("play2-war-core-common found in dependencies!")
    } else {
      s.log.error("play2-war-core-common not found in dependencies!")
      throw new IllegalArgumentException("play2-war-core-common not found in dependencies!")
    }

    val warDir = target.value
    val packageName = targetName.value.getOrElse(id + "-" + version.value)
    val war = warDir / (packageName + ".war")
    val manifestString = "Manifest-Version: 1.0\n"

    s.log.info("Packaging " + war.getCanonicalPath + " ...")

    IO.createDirectory(warDir)

    val allFilteredArtifacts = defaultFilteredArtifacts.value ++ filteredArtifacts.value

    allFilteredArtifacts.foreach {
      case (groupId, artifactId) =>
        s.log.debug("Ignoring dependency " + groupId + " -> " + artifactId)
    }

    val files: Traversable[(File, String)] = dependencies.
      filter(_.data.ext == "jar").flatMap { dependency =>
      val filename = for {
        module <- dependency.get(AttributeKey[ModuleID]("module-id"))
        artifact <- dependency.get(AttributeKey[Artifact]("artifact"))
        if (!allFilteredArtifacts.contains((module.organization, module.name)))
      } yield {
        // groupId.artifactId-version[-classifier].extension
        module.organization + "." + module.name + "-" + module.revision + artifact.classifier.map("-" + _).getOrElse("") + "." + artifact.extension
      }
      filename.map { fName =>
        val path = "WEB-INF/lib/" + fName
        Some(dependency.data -> path)
      }.getOrElse(None)
    } ++ unmanagedDependencies.map { unmanaged =>
      val path = "WEB-INF/lib/" + unmanaged.data.getName
      unmanaged.data -> path
//    } ++ {
//      if (explodedJar) {
//        s.log.info("Main artifacts " + packaged.map(_.getName).mkString("'", " ", "'") + " will be packaged exploded")
//
//        val explodedJarDir = target / "exploded"
//
//        IO.delete(explodedJarDir)
//        IO.createDirectory(explodedJarDir)
//
//        packaged.flatMap { jar =>
//          IO.unzip(jar, explodedJarDir).map {
//            file =>
//              val partialPath = IO.relativize(explodedJarDir, file).getOrElse(file.getName)
//
//              file -> ("WEB-INF/classes/" + partialPath)
//          }
//        }
//      } else packaged.get.map(jar => jar -> ("WEB-INF/lib/" + jar.getName))
//      val distFile = (sbt.Keys.`package` in Compile).value
//      val path = "WEB-INF/lib/" + distFile.getName
//      (distFile -> path) :: Nil
    }

    files.foreach { case (file, path) =>
      s.log.debug("Embedding file " + file + " -> " + path)
    }

    val webxmlFolder = webappResource.value / "WEB-INF"
    val webxml = webxmlFolder / "web.xml"

    // Web.xml generation
    servletVersion.value match {
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
                                 """ /* */)
        }

      }

      case "3.0" => handleWebXmlFileOnServlet30(webxml, s, disableWarningWhenWebxmlFileFound.value)

      case unknown => {
        s.log.warn("Unknown servlet container version: " + unknown + ". Force default 3.0 version")
        handleWebXmlFileOnServlet30(webxml, s, disableWarningWhenWebxmlFileFound.value)
      }
    }

    // Webapp resources
    val webappR = webappResource.value
    s.log.debug("Webapp resources directory: " + webappR.getAbsolutePath)

    val filesToInclude = getFiles(webappR).filter(f => f.isFile)

    val additionnalResources = filesToInclude.map {
      f =>
        f -> Path.relativizeFile(webappR, f).get.getPath
    }

    additionnalResources.foreach {
      r =>
        s.log.debug("Embedding " + r._1 + " -> /" + r._2)
    }

    val metaInfFolder = webappR / "META-INF"
    val manifest = if (metaInfFolder.exists()) {
      val option = metaInfFolder.listFiles.find(f =>
        manifestRegex.r.pattern.matcher(f.getAbsolutePath()).matches())
      if (option.isDefined) {
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
    val jarContent = files ++ additionnalResources

    IO.jar(jarContent, war, manifest)

    s.log.info("Packaging done.")

    war
  }

  def handleWebXmlFileOnServlet30(webxml: File, s: TaskStreams, disableWarn: Boolean) = {
    if (webxml.exists && !disableWarn) {
      s.log.warn("WEB-INF/web.xml found! As WAR package will be built for servlet 3.0 containers, check if this web.xml file is compatible with.")
    }
  }
}
