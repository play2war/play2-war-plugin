logLevel := Level.Warn

resolvers += Resolver.typesafeRepo("releases")

// addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.3.2")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.2")