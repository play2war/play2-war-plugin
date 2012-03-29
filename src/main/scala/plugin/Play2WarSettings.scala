package sbt

import Keys._
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
      
    war <<= warTask //
    // Add new tasks here
    )

}
