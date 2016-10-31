import sbt._
import Keys._
import java.io.File
import org.scalastyle.sbt.ScalastylePlugin
import bintray.BintrayPlugin
import bintray.BintrayPlugin.autoImport._

object Build extends Build {

  import BuildSettings._
  import Generators._

  val curDir = new File(".")

  val servlet31SampleProjectTargetDir = new File(curDir, "../sample/servlet31/target")
  val servlet31SampleWarPath = new File(servlet31SampleProjectTargetDir, "a-play2war-sample-servlet31-1.0-SNAPSHOT.war").getAbsolutePath

  val servlet30SampleProjectTargetDir = new File(curDir, "../sample/servlet30/target")
  val servlet30SampleWarPath = new File(servlet30SampleProjectTargetDir, "a-play2war-sample-servlet30-1.0-SNAPSHOT.war").getAbsolutePath

  val servlet25SampleProjectTargetDir = new File(curDir, "../sample/servlet25/target")
  val servlet25SampleWarPath = new File(servlet25SampleProjectTargetDir, "a-play2war-sample-servlet25-1.0-SNAPSHOT.war").getAbsolutePath

  val playDependency = "com.typesafe.play" %% "play-server" % play2Version % "provided->default" exclude ("javax.servlet", "servlet-api")

  //
  // Root project
  //
  lazy val root = project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ mavenSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCoreCommon, play2WarCoreservlet30, play2WarCoreservlet25, play2WarCoreservlet31, play2WarPlugin, play2WarIntegrationTests)

  //
  // Servlet implementations
  //
  lazy val play2WarCoreCommon = project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += playDependency,
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default"))

  lazy val play2WarCoreservlet31 = project(id = "play2-war-core-servlet31",
    base = file("core/servlet31"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += playDependency,
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided->default")) dependsOn play2WarCoreCommon

  lazy val play2WarCoreservlet30 = project(id = "play2-war-core-servlet30",
    base = file("core/servlet30"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += playDependency,
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default")) dependsOn play2WarCoreCommon

  lazy val play2WarCoreservlet25 = project(id = "play2-war-core-servlet25",
    base = file("core/servlet25"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      libraryDependencies += playDependency,
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default")) dependsOn play2WarCoreCommon

  //
  // Plugin
  //
  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ ivySettings ++ Seq(
      publishArtifact := true,
      scalaVersion := buildScalaVersionForSbt,
      scalaBinaryVersion := buildScalaVersionForSbtBinaryCompatible,
      sbtPlugin := true,

      sourceGenerators in Compile <+= sourceManaged in Compile map Play2WarVersion,

      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "com.typesafe.play" % "sbt-plugin" % play2Version % "provided->default(compile)" extra ("scalaVersion" -> buildScalaVersionForSbtBinaryCompatible, "sbtVersion" -> buildSbtVersionBinaryCompatible))
      }))

  //
  // Integration tests
  //
  lazy val play2WarIntegrationTests = project(id = "integration-tests",
    base = file("integration-tests"),
    settings = commonSettings ++ mavenSettings ++ Seq(
      sbtPlugin := false,
      publishArtifact := false,

      libraryDependencies += "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
      libraryDependencies += "junit" % "junit" % "4.10" % "test",
      libraryDependencies += "org.codehaus.cargo" % "cargo-core-uberjar" % "1.4.13" % "test",
      libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.13" % "test",

      parallelExecution in Test := false,
      testOptions in Test += Tests.Argument("-oD"),
      testOptions in Test += Tests.Argument("-Dwar.servlet31=" + servlet31SampleWarPath),
      testOptions in Test += Tests.Argument("-Dwar.servlet30=" + servlet30SampleWarPath),
      testOptions in Test += Tests.Argument("-Dwar.servlet25=" + servlet25SampleWarPath),
      testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))))

  //
  // Settings
  //
  def commonSettings = buildSettings ++ Seq(ScalastylePlugin.Settings: _*) ++ Seq(
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      publishArtifact in Test := false)

  object BuildSettings {

    val buildOrganization = "com.github.play2war"
    val defaultPlay2Version = "2.5.6"
    val play2Version = sys.props.get("play2.version").filterNot(_.isEmpty).getOrElse(defaultPlay2Version)
    val defaultBuildVersion = "1.5-beta1-SNAPSHOT"
    val buildVersion = sys.props.get("play2war.version").filterNot(_.isEmpty).getOrElse(defaultBuildVersion)
    val buildScalaVersion = "2.11.8"
    val buildScalaVersionForSbt = "2.10.6"
    val buildScalaVersionForSbtBinaryCompatible = CrossVersion.binaryScalaVersion(buildScalaVersionForSbt)
    val buildSbtVersion   = "0.13.11"
    val buildSbtVersionBinaryCompatible = "0.13"

    val buildSettings = Defaults.defaultSettings ++ Seq(
      resolvers           += Resolver.typesafeRepo("releases"),
      organization        := buildOrganization,
      version             := buildVersion,
      scalaVersion        := buildScalaVersion,
      scalaBinaryVersion  := CrossVersion.binaryScalaVersion(buildScalaVersion),
      checksums in update := Nil)

  }

  def commonIvyMavenSettings: Seq[Setting[_]] = Seq(
    licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    homepage := Some(url("https://github.com/play2war/play2-war-plugin"))
  )

  def ivySettings = commonIvyMavenSettings ++ Seq(
    publishMavenStyle := false,
    bintrayReleaseOnPublish := false,
    bintrayRepository := "sbt-plugins",
    bintrayOrganization := Some("play2war")
  )

  def mavenSettings = commonIvyMavenSettings ++ Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) {
        Some("snapshots" at nexus + "content/repositories/snapshots")
      } else {
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      }
    },
    pomExtra :=
  <scm>
    <url>git@github.com:play2war/play2-war-plugin.git</url>
    <connection>scm:git:git@github.com:play2war/play2-war-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>dlecan</id>
      <name>Damien Lecan</name>
      <email>dev@dlecan.com</email>
    </developer>
    <developer>
      <id>ysimon</id>
      <name>Yann Simon</name>
      <email>yann.simon.fr@gmail.com</email>
    </developer>
  </developers>
  )

  def propOr(name: String, value: String): String =
    (sys.props get name) orElse
      (sys.env get name) getOrElse
      value

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

  def project(id: String, base: File, settings: Seq[Def.Setting[_]] = Nil) =  
    Project(id, base, settings = settings)
}
