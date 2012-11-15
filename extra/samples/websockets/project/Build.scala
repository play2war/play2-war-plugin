import sbt._
import Keys._
import PlayProject._
import com.github.play2war.plugin._

object ApplicationBuild extends Build {

    val appName         = "p2w-websockets"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
        "com.github.play2war" %% "websocket-tomcat" % "0.9-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
        Play2WarKeys.servletVersion := "3.0",
        resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
//        resolvers += Resolver.url("Play2war plugin snapshot", url("http://repository-play-war.forge.cloudbees.com/snapshot/"))(Resolver.ivyStylePatterns)
    ).settings(Play2WarPlugin.play2WarSettings: _*)

}
