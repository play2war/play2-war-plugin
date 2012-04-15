// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += (Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns))

resolvers += "Play2war plugins snapshot" at "http://repository-play-war.forge.cloudbees.com/snapshot/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.0")

addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "0.2-SNAPSHOT")
