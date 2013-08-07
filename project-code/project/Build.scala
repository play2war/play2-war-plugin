import sbt._
import Keys._
import java.io.File
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object Build extends Build {

  import BuildSettings._
  import Generators._

  val nexus = "https://oss.sonatype.org/"
  val scalasbt = "http://repo.scala-sbt.org/scalasbt/"

  val curDir = new File(".")
  val servlet30SampleProjectTargetDir = new File(curDir, "../sample/servlet30/target")
  val servlet30SampleWarPath = new File(servlet30SampleProjectTargetDir, "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  val servlet25SampleProjectTargetDir = new File(curDir, "../sample/servlet25/target")
  val servlet25SampleWarPath = new File(servlet25SampleProjectTargetDir, "a-play2war-sample-servlet25-1.0-SNAPSHOT.war").getAbsolutePath

  //
  // Root project
  //
  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ mavenSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCoreCommon, play2WarCoreservlet30, play2WarCoreservlet25, play2WarPlugin, play2WarIntegrationTests)

  //
  // Servlet implementations
  //
  lazy val play2WarCoreCommon = Project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default"))

  lazy val play2WarCoreservlet30 = Project(id = "play2-war-core-servlet30",
    base = file("core/servlet30"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default")) dependsOn (play2WarCoreCommon)

  lazy val play2WarCoreservlet25 = Project(id = "play2-war-core-servlet25",
    base = file("core/servlet25"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default")) dependsOn (play2WarCoreCommon)

  //
  // Plugin
  //
  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ ivySettings ++ Seq(
      sbtPlugin := true,

      sourceGenerators in Compile <+= sourceManaged in Compile map Play2WarVersion,

      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "play" % "sbt-plugin" % play2Version % "provided->default(compile)" extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
      }))

  //
  // Integration tests
  //
  lazy val play2WarIntegrationTests = Project(id = "integration-tests",
    base = file("integration-tests"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      sbtPlugin := false,
      publishArtifact := false,

      libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test",
      libraryDependencies += "junit" % "junit" % "4.10" % "test",
      libraryDependencies += "org.codehaus.cargo" % "cargo-core-uberjar" % "1.3.1" % "test",
      libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.10" % "test",

      parallelExecution in Test := false,
      testOptions in Test += Tests.Argument("-oD"),
      testOptions in Test += Tests.Argument("-Dwar.servlet30=" + servlet30SampleWarPath),
      testOptions in Test += Tests.Argument("-Dwar.servlet25=" + servlet25SampleWarPath),
      testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))))

  //
  // Settings
  //
  def commonSettings = buildSettings ++
    Seq(
      javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      EclipseKeys.withSource := true,

      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in Test := false)

  object BuildSettings {

    val buildOrganization = "com.github.play2war"
    val defaultPlay2Version = "2.0.6"
    val play2Version = Option(System.getProperty("play2.version")).filterNot(_.isEmpty).getOrElse(defaultPlay2Version)
    val buildVersion = "0.8.2-SNAPSHOT"

    val buildSettings = Defaults.defaultSettings ++ Seq(
      resolvers += "Typsafe releases" at "http://repo.typesafe.com/typesafe/releases/",
      organization := buildOrganization,
      version := buildVersion)

  }

  def commonIvyMavenSettings: Seq[Setting[_]] = Seq(
    licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/dlecan/play2-war-plugin"))
  )

  def ivySettings = commonIvyMavenSettings ++ Seq(
    publishMavenStyle := false,
    publishTo <<= (version) {
      version: String => {
        val (name, url) = if (version.contains("-SNAPSHOT")) {
          ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
        } else {
          ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
        }
        Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
      }
    }
  )

  def mavenSettings = commonIvyMavenSettings ++ Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo <<= (version) {
      version: String => {
          if (version.trim.endsWith("SNAPSHOT")) {
            Some("snapshots" at nexus + "content/repositories/snapshots")
          } else {
            Some("releases" at nexus + "service/local/staging/deploy/maven2")
          }
        }
    },
    pomExtra := (
  <scm>
    <url>git@github.com:dlecan/play2-war-plugin.git</url>
    <connection>scm:git:git@github.com:dlecan/play2-war-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>dlecan</id>
      <name>Damien Lecan</name>
      <email>dev@dlecan.com</email>
    </developer>
  </developers>))

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
