sbtPlugin := true

organization := "com.github.play2.warplugin"

name := "play2-war-plugin"

resolvers ++= Seq(
    DefaultMavenRepository,
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "play"			%% "play"          % "2.0"
)

libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) => 
	Seq(
		"play"			% "sbt-plugin"    % "2.0" extra("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion)
	)
}

// Download sources when executing "eclipse" command
EclipseKeys.withSource := true