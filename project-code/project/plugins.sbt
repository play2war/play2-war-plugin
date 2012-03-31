resolvers ++= Seq(
    DefaultMavenRepository,
	"gseitz@github" at "http://gseitz.github.com/maven/",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

// addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.1")

// addSbtPlugin("name.heikoseeberger.sbtproperties" % "sbtproperties" % "1.0.1")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0")