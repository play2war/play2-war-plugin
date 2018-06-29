import com.github.play2war.plugin.{Play2WarKeys, Play2WarPlugin}

lazy val appName = "a-play2war-sample-"
lazy val appVersion = "1.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version := appVersion,

  //      resolvers += "Local Repository" at "http://localhost:8090/publish",
  resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  // Doesn't work
  // resolvers += Resolver.defaultLocal,
  resolvers += Resolver.sonatypeRepo("snapshots"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

  scalaVersion := propOr("play2-war.sbt.scala.version", "2.10.5")
)

lazy val root = Project(appName + "parent", file("."))
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false
  )
  .aggregate(common, servlet25, servlet30, servlet31)

lazy val playProjectSettings = Seq(
  libraryDependencies ++= Seq(ws, javaWs)
)

lazy val commonAppDependencies = Seq(
  "com.typesafe" % "config" % "1.3.2"
)

lazy val common = Project(appName + "common", file("common"))
  .enablePlugins(play.sbt.PlayJava)
  .settings(commonSettings ++ playProjectSettings: _*)

lazy val appDependencies = commonAppDependencies

lazy val warProjectSettings = playProjectSettings ++ Play2WarPlugin.play2WarSettings ++ Seq(
  Play2WarKeys.filteredArtifacts := Seq()
)

lazy val servlet25 = Project(appName + "servlet25", file("servlet25"))
  .enablePlugins(play.sbt.PlayJava)
  .settings(commonSettings ++ warProjectSettings: _*)
  .settings(
    libraryDependencies ++= appDependencies,
    Play2WarKeys.servletVersion := "2.5"
  )
  .dependsOn(common)

lazy val servlet30 = Project(appName + "servlet30", file("servlet30"))
  .enablePlugins(play.sbt.PlayJava)
  .settings(commonSettings ++ warProjectSettings: _*)
  .settings(
    libraryDependencies ++= appDependencies,
    Play2WarKeys.servletVersion := "3.0",
    Play2WarKeys.explodedJar := true
  )
  .dependsOn(common)

lazy val servlet31 = Project(appName + "servlet31", file("servlet31"))
  .enablePlugins(play.sbt.PlayJava)
  .settings(commonSettings ++ warProjectSettings: _*)
  .settings(
    libraryDependencies ++= appDependencies,
    Play2WarKeys.servletVersion := "3.1"
  )
  .dependsOn(common)

def propOr(name: String, value: String): String =
  (sys.props get name) orElse
    (sys.env get name) getOrElse
    value