import sbt._
import Keys._
import PlayProject._
import com.github.play2war.plugin._

object ApplicationBuild extends Build {

  lazy val appName = "a-play2war-sample-"
  lazy val appVersion = "1.0-SNAPSHOT"

  lazy val commonSettings = Defaults.defaultSettings ++ Seq(
    resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers += "Play2war plugin snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",

    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),

    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,

    publishTo := Some(Resolver.file("file", file(Path.userHome.absolutePath + "/.ivy2/publish"))),
    publishMavenStyle := true)

  lazy val parent = Project(id = appName + "parent", base = file("."), settings = commonSettings ++ Seq(publishArtifact := false)) aggregate (common, servlet25, servlet30)

  lazy val playProjectSettings = Seq(
    //
  )
    
  lazy val appDependencies = Seq(
    // Add your project dependencies here
  )

  lazy val common = PlayProject(appName + "common", appVersion, appDependencies, mainLang = SCALA, path = file("common"), settings = commonSettings ++ playProjectSettings)

  lazy val warProjectSettings = playProjectSettings ++ Play2WarPlugin.play2WarSettings ++ Seq(
    publishArtifact in (Compile, packageBin) := false
  )

  lazy val servlet25 = PlayProject(appName + "servlet25", appVersion, appDependencies, mainLang = SCALA, path = file("servlet25"), settings = commonSettings ++ warProjectSettings ++ Seq(Play2WarKeys.servletVersion := "2.5")) dependsOn (common)

  lazy val servlet30 = PlayProject(appName + "servlet30", appVersion, appDependencies, mainLang = SCALA, path = file("servlet30"), settings = commonSettings ++ warProjectSettings ++ Seq(Play2WarKeys.servletVersion := "3.0")) dependsOn (common)

}
