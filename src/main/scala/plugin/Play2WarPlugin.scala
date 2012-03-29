package com.github.play2.warplugin

import sbt._
import Keys._

object Play2WarPlugin extends Plugin with Play2WarCommands with Play2WarSettings {
	
  override lazy val settings = defaultSettings
//
//  lazy val myCommand =
//    Command.command("hello") { (state: State) =>
//      println("Hi!")
//      state
//    }
}