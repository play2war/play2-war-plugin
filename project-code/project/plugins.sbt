resolvers ++= Seq(
    DefaultMavenRepository,
//  Moved
//  TODO: update location
//	"gseitz@github" at "http://gseitz.github.com/maven/",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

// addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.0")

scalaVersion := "2.9.2"
