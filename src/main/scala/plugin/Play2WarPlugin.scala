package com.github.play2.warplugin

import sbt._
import Keys._

object Play2WarPlugin extends Plugin {
	
  override lazy val settings = Seq(commands += myCommand)

  lazy val myCommand =
    Command.command("hello") { (state: State) =>
      println("Hi!")
      state
    }
}