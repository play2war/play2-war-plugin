logLevel := Level.Warn

resolvers += Resolver.typesafeRepo("releases")

// addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.2")