import sbt._
import Keys._

object Build extends Build {

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCore, play2WarPlugin)

  lazy val play2WarCore = Project(id = "play2-war-core",
    base = file("core"),
    settings = commonSettings ++ Seq(
      sbtPlugin := false,
      libraryDependencies ++= Seq("play" %% "play" % "2.0")))

  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ Seq(
      sbtPlugin := true,
      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "play" % "sbt-plugin" % "2.0" extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
      })) /* dependsOn(play2WarCore) */

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.github.play2war",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      resolvers += ("Typsafe releases" at "http://repo.typesafe.com/typesafe/releases/"),
	  publishTo <<= (version) {
		version: String =>
		  if (version.trim.endsWith("SNAPSHOT")) Some("snapshot" at cloudbees + "snapshot/")
		  else                                   Some("release"  at cloudbees + "release/")
	  },
      credentials += Credentials(file("/private/play-war/.credentials")),
      publishMavenStyle := true,
      publishArtifact in Test := false
    )
}
