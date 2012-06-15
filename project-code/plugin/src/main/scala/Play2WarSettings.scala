package com.github.play2.warplugin

import sbt.{ `package` => _, _ }
import sbt.Keys._
import PlayKeys._
import Play2WarKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val defaultSettings = Seq[Setting[_]](
    //    defaultServletVersion <<= "3.x",

    // War attifact
    artifact in war <<= moduleName(n => Artifact(n, "war", "war")),

    // Bind war building to "war" task
    war <<= warTask,

    // Bind war task to "package" task (phase)
    `package` <<= war //
    ) ++
    // Attach war artifact. War file is published on "publish-local" and "publish"
    Seq(addArtifact(artifact in (Compile, war), war).settings: _*)

}
