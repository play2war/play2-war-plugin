import sbt._
import Keys._
import java.io.File
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object Build extends Build {

  import BuildSettings._
  import Generators._

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"

  val curDir = new File(".")
  val servlet30SampleProjectTargetDir = new File(curDir, "../sample/servlet30/target")
  val servlet30SampleWarPath = new File(servlet30SampleProjectTargetDir, "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  val servlet25SampleProjectTargetDir = new File(curDir, "../sample/servlet25/target")
  val servlet25SampleWarPath = new File(servlet25SampleProjectTargetDir, "a-play2war-sample-servlet25-1.0-SNAPSHOT.war").getAbsolutePath

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCoreCommon, play2WarCoreservlet30, play2WarCoreservlet25, play2WarPlugin, play2WarIntegrationTests)

  lazy val play2WarCoreCommon = Project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api") exclude ("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default"))

  lazy val play2WarCoreservlet30 = Project(id = "play2-war-core-servlet30",
    base = file("core/servlet30"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api") exclude ("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default")) dependsOn (play2WarCoreCommon)

  lazy val play2WarCoreservlet25 = Project(id = "play2-war-core-servlet25",
    base = file("core/servlet25"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api") exclude ("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default")) dependsOn (play2WarCoreCommon)

  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ Seq(
      sbtPlugin := true,

      sourceGenerators in Compile <+= sourceManaged in Compile map Play2WarVersion,

      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "play" % "sbt-plugin" % play2Version extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
      }))

  lazy val play2WarIntegrationTests = Project(id = "integration-tests",
    base = file("integration-tests"),
    settings = commonSettings ++ Seq(
      sbtPlugin := false,
      publishArtifact := false,

      libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test",
      libraryDependencies += "junit" % "junit" % "4.10" % "test",
      libraryDependencies += "org.codehaus.cargo" % "cargo-core-uberjar" % "1.2.3" % "test",
      libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.9" % "test",

      parallelExecution in Test := false,
      testOptions in Test += Tests.Argument("-oD"),
      testOptions in Test += Tests.Argument("-Dwar.servlet30=" + servlet30SampleWarPath),
	  testOptions in Test += Tests.Argument("-Dwar.servlet25=" + servlet25SampleWarPath),
      testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))))

  def commonSettings = buildSettings ++
    Seq(
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      EclipseKeys.withSource := true,

      resolvers += ("Typsafe releases" at "http://repo.typesafe.com/typesafe/releases/"),

      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in Test := false,

      //      publishTo := Some(Resolver.file("file",  file(Path.userHome.absolutePath + "/.ivy2/publish")) ),
      //      publishTo <<= (version) {
      //		version: String =>
      //		  if (version.trim.endsWith("SNAPSHOT")) Some("snapshot" at cloudbees + "snapshot/")
      //		  else                                   Some("release"  at cloudbees + "release/")
      //	  },
      //      credentials += Credentials(file("/private/play-war/.credentials")),
      //      credentials += Credentials(file(Path.userHome.absolutePath + "/.ivy2/.credentials")),
      publishMavenStyle := true)

  object BuildSettings {

    val buildOrganization = "com.github.play2war"
    val defaultPlay2Version = "2.0.2"
    val play2Version = Option(System.getProperty("play2.version")).filterNot(_.isEmpty).getOrElse(defaultPlay2Version)
    val buildVersion = "0.7.3"

    val buildSettings = Defaults.defaultSettings ++ Seq(
      organization := buildOrganization,
      version := buildVersion)

  }

  object Generators {

    val Play2WarVersion = { dir: File =>
      val file = dir / "Play2WarVersion.scala"
      IO.write(file,
        """|package com.github.play2war.plugin
                   |
                   |object Play2WarVersion {
                   |    val current = "%s"
                   |}
                """.stripMargin.format(BuildSettings.buildVersion))
      Seq(file)
    }

  }
}
