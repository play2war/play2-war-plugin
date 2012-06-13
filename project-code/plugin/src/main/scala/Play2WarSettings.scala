package com.github.play2.warplugin

import sbt.{ `package` => _, _ }
import PlayKeys._

trait Play2WarSettings {
  this: Play2WarCommands =>

  lazy val defaultSettings = Seq[Setting[_]](
    //
    //    resolvers ++= Seq(
    //      "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/",
    //      "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/"
    //    ),
    //

    //    defaultServletVersion <<= "3.x",
//    `package` ~= { result =>
//      println("in package, something")
//      result
//    },
    war <<= warTask //
    // Add new tasks here
    )

}
