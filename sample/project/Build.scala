import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "a_warification"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    "com.github.play2war" %% "play2-war-core-servlet30" % "0.7-SNAPSHOT")

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    //      resolvers += "Local Repository" at "http://localhost:8090/publish",
    resolvers += (Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)),
    resolvers += "Play2war plugins" at "http://repository-play-war.forge.cloudbees.com/release/",

    publishArtifact in (Compile, packageBin) := false,
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false,

    publishTo := Some(Resolver.file("file", file(Path.userHome.absolutePath + "/.ivy2/publish"))),
    publishMavenStyle := true)/* dependsOn(core)

    lazy val core = Project("core", file("core"))*/

}
