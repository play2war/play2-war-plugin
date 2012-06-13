package com.github.play2.warplugin

import sbt._

trait Play2WarKeys {

  val war = TaskKey[File]("war", "Build the standalone application package as a WAR file")

  val servletVersion: SettingKey[String] =
    SettingKey[String](
      "servletVersion",
      "Servlet container version (2.4, 3.0)?")

}
object Play2WarKeys extends Play2WarKeys