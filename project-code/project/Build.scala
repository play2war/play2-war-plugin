import sbt._
import Keys._
import PlayProject._

object Build extends Build {

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"
  val local = Path.userHome.absolutePath + "/.ivy2/publish/"

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCore, play2WarPlugin, play2WarIntegrationTests)

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
      }))

  lazy val play2WarIntegrationTests = PlayProject(name = "integration-tests",
    path = file("integration-tests"),
    mainLang = SCALA,
    settings = commonSettings ++ Seq(
//      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
//        Seq(
//          "play" % "sbt-plugin" % "2.0" extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
//      }
    )
  ) dependsOn(play2WarCore, play2WarPlugin)

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.github.play2war",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      resolvers += ("Typsafe releases" at "http://repo.typesafe.com/typesafe/releases/"),
	  publishTo <<= (version) {
		version: String =>
		// Webdav support is buggy on Cloudbees. Pulish on local repository instead
		  if (version.trim.endsWith("SNAPSHOT")) Some("snapshot" at cloudbees + "snapshot/")
		  else                                   Some("release"  at cloudbees + "release/")
	  },
      credentials += Credentials(file("/private/play-war/.credentials")),
//      credentials += Credentials(file(Path.userHome.absolutePath + "/.ivy2/.credentials")),
      publishMavenStyle := true,
      publishArtifact in Test := false
    )
}
