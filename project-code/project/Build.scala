import sbt._
import Keys._
import java.io.File

object Build extends Build {

  val play2Version = "2.0.1"

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"
  val curDir = new File(".")
  val sampleProjectTargetDir = new File(curDir, "../sample/target")
  val sampleWarPath = new File(sampleProjectTargetDir, "a_warification-1.0-SNAPSHOT.war").getAbsolutePath

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)
  ) aggregate (play2WarCoreCommon, play2WarCoreServlet3x, play2WarCoreServlet2x, play2WarPlugin, play2WarIntegrationTests)

  lazy val play2WarCoreCommon = Project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude("javax.servlet", "servlet-api") exclude("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default" 
    )
  )
      
  lazy val play2WarCoreServlet3x = Project(id = "play2-war-core-servlet3x",
    base = file("core/servlet3x"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude("javax.servlet", "servlet-api") exclude("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default"
    )
  ) dependsOn(play2WarCoreCommon)

  lazy val play2WarCoreServlet2x = Project(id = "play2-war-core-servlet2x",
    base = file("core/servlet2x"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude("javax.servlet", "servlet-api") exclude("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default"
    )
  ) dependsOn(play2WarCoreCommon)
      
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
