// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
// Doesn't work
// resolvers += Resolver.defaultLocal

resolvers += Resolver.typesafeRepo("releases")

resolvers += Resolver.sbtPluginRepo("snapshots")

libraryDependencies += "com.typesafe" % "config" % "1.3.2"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % Option(System.getProperty("play.version")).getOrElse("2.6.15"))

addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "1.6.0-SNAPSHOT")
