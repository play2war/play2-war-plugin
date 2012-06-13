package com.github.play2.warplugin

import sbt.{ `package` => _, _ }
import sbt.Keys._
import PlayKeys._
import Play2WarKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val defaultSettings = Seq[Setting[_]](
    //    defaultServletVersion <<= "3.x",
    //    artifact in war <<= moduleName(n => Artifact(n, "war", "war")),
    //    addArtifact(artifact in (Compile, war), warTask),
    war <<= warTask,
    `package` <<= war //
    // Add new tasks here
    )

}
