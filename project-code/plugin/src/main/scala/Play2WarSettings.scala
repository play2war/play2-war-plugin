package com.github.play2war.plugin

import sbt.{ `package` => _, _ }
import sbt.Keys._
import PlayKeys._
import com.github.play2war.plugin.Play2WarKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val play2WarSettings = Seq[Setting[_]](

    libraryDependencies <++= (servletVersion) { (v) =>
      val servletVersionString = v match {
        case "2.5" => "25"
        case _ => "30"
      }
      Seq("com.github.play2war" %% ("play2-war-core-servlet" + servletVersionString) % com.github.play2war.plugin.Play2WarVersion.current)
    },
    
    webappResource <<= baseDirectory / "war",

    // War artifact
    artifact in war <<= moduleName(n => Artifact(n, "war", "war")),

    // Bind war building to "war" task
    war <<= warTask,

    // Bind war task to "package" task (phase)
    `package` <<= war //
    )

// TODO: the line below was causing the following error in "sbt play-package-everything" (and "sbt war"):
// [error] java.lang.AssertionError: assertion failed: Internal task engine error: nothing running.  This usually indicates a cycle in tasks.
// [error]   Calling tasks (internal task engine state):
// [error] Task((task-definition-key: ScopedKey(Scope(Select(ProjectRef(file:/home/sqs/src/play2-war-plugin/sample/,a-play2war-sample-servlet25)),Global,Global,Global),play-package-everything))) -> Calling
// [error] 	Task((task-definition-key: ScopedKey(Scope(Select(ProjectRef(file:/home/sqs/src/play2-war-plugin/sample/,a-play2war-sample-servlet30)),Global,Global,Global),play-package-everything))) -> Calling

    // Attach war artifact. War file is published on "publish-local" and "publish"
//    Seq(addArtifact(artifact in (Compile, war), war).settings: _*)

}
