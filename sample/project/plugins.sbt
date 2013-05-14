// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += Resolver.defaultLocal

resolvers += Resolver.typesafeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % Option(System.getProperty("play.version")).getOrElse("2.1.0"))

//addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0")

addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "1.0-SNAPSHOT")
