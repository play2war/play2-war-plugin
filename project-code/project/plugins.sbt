logLevel := Level.Warn

resolvers += Resolver.typesafeRepo("releases")

// addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.3.2")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")