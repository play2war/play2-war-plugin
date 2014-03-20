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

import sbt._

trait Play2WarKeys {

  lazy val war = TaskKey[File]("war", "Build the standalone application package as a WAR file")

  lazy val servletVersion: SettingKey[String] =
    SettingKey[String](
      "servletVersion",
      "Servlet container version (2.5, 3.0, 3.1)")

  lazy val webappResource = SettingKey[File]("webapp-resources")
  
  lazy val disableWarningWhenWebxmlFileFound = SettingKey[Boolean]("disable-warning-when-webxml-file-found")

  lazy val targetName = SettingKey[Option[String]]("targetName", "The name of the WAR file generated")

  lazy val defaultFilteredArtifacts = SettingKey[Seq[(String, String)]]("defaultFilteredArtifacts", "Artifacts filtered from WAR by default")

  lazy val filteredArtifacts = SettingKey[Seq[(String, String)]]("filteredArtifacts", "User's artifacts filtered from WAR")
  
  lazy val explodedJar = SettingKey[Boolean]("explodedJar", "Should this project's jar be exploded ?")
}

object Play2WarKeys extends Play2WarKeys