import sbt._
import Keys._

object Build extends Build {

    lazy val root = Project(id = "play2-war",
								base = file("."),
								settings = commonSettings ++ Seq(
									publishArtifact := false  
									)
							) aggregate(play2WarCore, play2WarPlugin)

    lazy val play2WarCore = Project(id = "play2-war-core",
								base = file("core"),
								settings = commonSettings ++ Seq(
									sbtPlugin := false,
									libraryDependencies ++= Seq("play"			%% "play"          % "2.0")
								)
						   )

    lazy val play2WarPlugin = Project(id = "play2-war-plugin",
								base = file("plugin"),
								settings = commonSettings ++ Seq(
									sbtPlugin := true,
									libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) => 
										Seq(
											"play"			% "sbt-plugin"    % "2.0" extra("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion)
										)
									}
								)
							) dependsOn(play2WarCore)

def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.github.play2.warplugin",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation")
    )						   
}