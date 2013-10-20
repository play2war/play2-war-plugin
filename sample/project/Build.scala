import sbt._
import Keys._
import play.Project._
import com.github.play2war.plugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object ApplicationBuild extends Build {

  lazy val appName = "a-play2war-sample-"
  lazy val appVersion = "1.0-SNAPSHOT"

  lazy val commonSettings = Defaults.defaultSettings ++ playJavaSettings ++ Seq(
    //      resolvers += "Local Repository" at "http://localhost:8090/publish",
    resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    // Doesn't work
    // resolvers += Resolver.defaultLocal,
    resolvers += Resolver.sonatypeRepo("snapshots"),

    EclipseKeys.withSource := true,
    EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE16),

    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),

    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,

    publishTo := Some(Resolver.file("file", file(Path.userHome.absolutePath + "/.ivy2/publish"))),
    publishMavenStyle := true)

  lazy val parent = Project(id = appName + "parent", base = file("."), settings = commonSettings ++ Seq(publishArtifact := false)) aggregate (common, servlet25, servlet30)

  lazy val playProjectSettings = Seq(
    //
  )
    
  lazy val commonAppDependencies = Seq(
    javaCore
  )

  lazy val common = play.Project(appName + "common", appVersion, commonAppDependencies, path = file("common"), settings = commonSettings ++ playProjectSettings)

  override def rootProject = Some(parent)

  lazy val appDependencies = commonAppDependencies ++ Seq(
    "com.github.play2war.ext" %% "redirect-playlogger" % "1.0.1"
  )

  lazy val warProjectSettings = playProjectSettings ++ Play2WarPlugin.play2WarSettings ++ Seq(
    publishArtifact in (Compile, packageBin) := false,
    Play2WarKeys.filteredArtifacts := Seq()
  )

  lazy val servlet25 = play.Project(
      appName + "servlet25", appVersion, appDependencies, path = file("servlet25"), 
      settings = commonSettings ++ warProjectSettings ++ Seq(Play2WarKeys.servletVersion := "2.5")
  ) dependsOn (common)

  lazy val servlet30 = play.Project(
      appName + "servlet30", appVersion, appDependencies, path = file("servlet30"), 
      settings = commonSettings ++ warProjectSettings ++ Seq(Play2WarKeys.servletVersion := "3.0")
  ) dependsOn (common)

}
