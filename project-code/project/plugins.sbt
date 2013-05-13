logLevel := Level.Warn

resolvers += Resolver.typesafeRepo("releases")

// addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.1")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.2.0")