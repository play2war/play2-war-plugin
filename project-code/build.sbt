resolvers ++= Seq(
    DefaultMavenRepository,
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

// Download sources when executing "eclipse" command
EclipseKeys.withSource := true