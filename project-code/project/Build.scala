import sbt._
import Keys._
import java.io.File
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object Build extends Build {

  val play2Version = "2.0.2"

  val cloudbees = "https://repository-play-war.forge.cloudbees.com/"
  val curDir = new File(".")
  val sampleProjectTargetDir = new File(curDir, "../sample/target")
  val sampleWarPath = new File(sampleProjectTargetDir, "a_warification-1.0-SNAPSHOT.war").getAbsolutePath

  lazy val root = Project(id = "play2-war",
    base = file("."),
    settings = commonSettings ++ Seq(
      publishArtifact := false)
  ) aggregate (play2WarCoreCommon, play2WarCoreservlet30, play2WarCoreservlet25, play2WarPlugin, play2WarIntegrationTests)

  lazy val play2WarCoreCommon = Project(id = "play2-war-core-common",
    base = file("core/common"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude("javax.servlet", "servlet-api") exclude("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % "provided->default" 
    )
  )

  lazy val play2WarCoreservlet30 = Project(id = "play2-war-core-servlet30",
    base = file("core/servlet30"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "play" %% "play" % play2Version % "provided->default" exclude("javax.servlet", "servlet-api") exclude("javax.servlet", "javax.servlet-api"),
      libraryDependencies += "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided->default"
    )
  ) dependsOn(play2WarCoreCommon)

  lazy val play2WarCoreservlet25 = Project(id = "play2-war-core-servlet25",
    base = file("core/servlet25"),
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

      libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test",
      libraryDependencies += "junit" % "junit" % "4.10" % "test",
      libraryDependencies += "org.codehaus.cargo" % "cargo-core-uberjar" % "1.2.2" % "test",
      libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.9" % "test",

      parallelExecution in Test := false,
      testOptions in Test += Tests.Argument("-oD"),
      testOptions in Test += Tests.Argument("-Dwar=" + sampleWarPath),
      testListeners <<= target.map(t => Seq(new eu.henkelmann.sbt.JUnitXmlTestsListener(t.getAbsolutePath)))
  ))

  def commonSettings = Defaults.defaultSettings ++
    Seq(
      organization := "com.github.play2war",
      // version is defined in version.sbt in order to support sbt-release
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
      publishMavenStyle := true
    )
}
