package com.github.play2war.plugin

import sbt._

trait Play2WarKeys {

  lazy val war = TaskKey[File]("war", "Build the standalone application package as a WAR file")

  lazy val servletVersion: SettingKey[String] =
    SettingKey[String](
      "servletVersion",
      "Servlet container version (2.5, 3.0)")
      
  lazy val webappResource = SettingKey[File]("webapp-resources")

}
object Play2WarKeys extends Play2WarKeys