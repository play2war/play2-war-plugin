import sbt._
import Keys._
import java.io.File
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object Build extends Build {

  import BuildSettings._
  import Generators._

  val nexus = "https://oss.sonatype.org/"

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
    settings = commonSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCoreCommon, play2WarCoreservlet30, play2WarCoreservlet25, play2WarPlugin, play2WarIntegrationTests)

  //
  // Servlet implementations
  //
  lazy val play2WarCoreCommon = Project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default"))

  lazy val play2WarCoreservlet30 = Project(id = "play2-war-core-servlet30",
    base = file("core/servlet30"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default")) dependsOn (play2WarCoreCommon)

  lazy val play2WarCoreservlet25 = Project(id = "play2-war-core-servlet25",
    base = file("core/servlet25"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default")) dependsOn (play2WarCoreCommon)

  //
  // Plugin
  //
  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ Seq(
      scalaVersion := buildScalaVersionForSbt,
      scalaBinaryVersion  := CrossVersion.binaryScalaVersion(buildScalaVersionForSbt),
      sbtPlugin := true,

      sourceGenerators in Compile <+= sourceManaged in Compile map Play2WarVersion,

      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "play" % "sbt-plugin" % play2Version % "provided->default(compile)" extra ("scalaVersion" -> buildScalaVersionForSbt, "sbtVersion" -> buildSbtVersionBinaryCompatible))
      }))

  //
  // Integration tests
  //
  lazy val play2WarIntegrationTests = Project(id = "integration-tests",
    base = file("integration-tests"),
    settings = commonSettings ++ Seq(
      sbtPlugin := false,
      publishArtifact := false,
      scalaBinaryVersion := buildScalaVersion,

      libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
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
  def commonSettings = buildSettings ++ mavenSettings ++
    Seq(
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      EclipseKeys.withSource := true,

      resolvers += ("Typsafe releases" at "http://repo.typesafe.com/typesafe/releases/"),

      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in Test := false,

      // Publishing settings
      // Snapshots : Ivy style
      // Releases : Maven style

      publishTo <<= (version) {
        version: String => {
      	    if (version.trim.endsWith("SNAPSHOT")) {
              Some("snapshots" at nexus + "content/repositories/snapshots")
            } else {
              Some("releases"  at nexus + "service/local/staging/deploy/maven2")
            }
          }
      },
      
      credentials += Credentials(file("/private/play-war/.credentials")))

  object BuildSettings {

    val buildOrganization = "com.github.play2war"
    val defaultPlay2Version = "2.1-RC2"
    val play2Version = Option(System.getProperty("play2.version")).filterNot(_.isEmpty).getOrElse(defaultPlay2Version)
    val buildVersion = "0.9-RC2"
    val buildScalaVersion = "2.10.0"
    val buildScalaVersionForSbt = "2.9.2"
    val buildSbtVersion   = "0.12.2-RC2"
    val buildSbtVersionBinaryCompatible = "0.12"

    val buildSettings = Defaults.defaultSettings ++ Seq(
      organization        := buildOrganization,
      version             := buildVersion,
      scalaVersion        := buildScalaVersion,
      scalaBinaryVersion  := CrossVersion.binaryScalaVersion(buildScalaVersion),
      checksums in update := Nil)

  }


  def mavenSettings = Seq(
      publishMavenStyle := true,
      pomIncludeRepository := { _ => false },
      licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
      homepage := Some(url("https://github.com/dlecan/play2-war-plugin")),
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
