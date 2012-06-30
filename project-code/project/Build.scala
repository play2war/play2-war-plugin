import sbt._
import Keys._
import java.io.File

object Build extends Build {

  val play2Version = "2.0.2"

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"
  val curDir = new File(".")
  val sampleProjectTargetDir = new File(curDir, "../sample/target")
  val sampleWarPath = new File(sampleProjectTargetDir, "a_warification-1.0-SNAPSHOT.war").getAbsolutePath

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)) aggregate (play2WarCore, play2WarPlugin, play2WarIntegrationTests)

  lazy val play2WarCore = Project(id = "play2-war-core",
    base = file("core"),
    settings = commonSettings ++ Seq(
      sbtPlugin := false,
      libraryDependencies ++= Seq("play" %% "play" % play2Version % "provided->default")))

  lazy val play2WarPlugin = Project(id = "play2-war-plugin",
    base = file("plugin"),
    settings = commonSettings ++ Seq(
      sbtPlugin := true,
      libraryDependencies <++= (scalaVersion, sbtVersion) { (scalaVersion, sbtVersion) =>
        Seq(
          "play" % "sbt-plugin" % play2Version extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
      }))

  lazy val play2WarIntegrationTests = Project(id = "integration-tests",
    base = file("integration-tests"),
    settings = commonSettings ++ Seq(
      sbtPlugin := false,
      publishArtifact := false,
      libraryDependencies ++= Seq(
          "org.scalatest" %% "scalatest" % "1.7.2" % "test",
          "junit" % "junit" % "4.10" % "test",
          "org.codehaus.cargo" % "cargo-core-uberjar" % "1.2.2" % "test",
          "net.sourceforge.htmlunit" % "htmlunit" % "2.9" % "test"
      ),
      parallelExecution in Test := false,
      testOptions in Test += Tests.Argument("-oD"),
      testOptions in Test += Tests.Argument("-Dwar=" + sampleWarPath)
  ))

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.github.play2war",
      // version is defined in version.sbt in order to support sbt-release
      scalacOptions ++= Seq("-unchecked", "-deprecation"),

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
      publishMavenStyle := true
    )
}
