import sbt._
import Keys._
import play.Project._
import com.github.play2war.plugin._

object ApplicationBuild extends Build {

    val appName         = "jboss-ebean"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      javaCore,
      javaJdbc,
      javaEbean,
      "com.dlecan.reflections" % "jboss7-vfs-integration" % "1.0.2",
      "com.github.play2war.ext" %% "redirect-playlogger" % "1.0.0"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      Play2WarKeys.servletVersion := "3.0",
      resolvers += Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)
    ).settings(Play2WarPlugin.play2WarSettings: _*)

}
            
