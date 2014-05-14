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
import sbt.Keys._
import com.github.play2war.plugin.Play2WarKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val play2WarSettings = Seq[Setting[_]](

    libraryDependencies <++= servletVersion {
      (v) =>
        val servletVersionString = v match {
          case "2.5" => "25"
          case "3.1" => "31"
          case _ => "30"
        }
        Seq("com.github.play2war" %% ("play2-war-core-servlet" + servletVersionString) % com.github.play2war.plugin.Play2WarVersion.current)
    },

    webappResource <<= baseDirectory / "war",

    // War artifact
    artifact in war <<= moduleName(n => Artifact(n, "war", "war")),

    targetName := None,

    disableWarningWhenWebxmlFileFound := false,

    defaultFilteredArtifacts := Seq(
      ("javax.servlet", "servlet-api"),
      ("javax.servlet", "javax.servlet-api")
    ),

    filteredArtifacts := Seq(),
    
    explodedJar := false,

    // Bind war building to "war" task
    war <<= warTask

    // Bind war task to "package" task (phase)
    //sbt.Keys.`package` <<= war //
  )

  // TODO: the line below was causing the following error in "sbt play-package-everything" (and "sbt war"):
  // [error] java.lang.AssertionError: assertion failed: Internal task engine error: nothing running.  This usually indicates a cycle in tasks.
  // [error]   Calling tasks (internal task engine state):
  // [error] Task((task-definition-key: ScopedKey(Scope(Select(ProjectRef(file:/home/sqs/src/play2-war-plugin/sample/,a-play2war-sample-servlet25)),Global,Global,Global),play-package-everything))) -> Calling
  // [error]  Task((task-definition-key: ScopedKey(Scope(Select(ProjectRef(file:/home/sqs/src/play2-war-plugin/sample/,a-play2war-sample-servlet30)),Global,Global,Global),play-package-everything))) -> Calling

  // Attach war artifact. War file is published on "publish-local" and "publish"
  //    Seq(addArtifact(artifact in (Compile, war), war).settings: _*)

}

