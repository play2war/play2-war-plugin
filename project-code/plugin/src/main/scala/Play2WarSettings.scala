package com.github.play2.warplugin

import sbt.{ `package` => _, _ }
import sbt.Keys._
import PlayKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val defaultSettings = Seq[Setting[_]](
    //    defaultServletVersion <<= "3.x",
    war <<= warTask,
    `package` <<= warTask //
    // Add new tasks here
    )

}
