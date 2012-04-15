import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "a_warification"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
	  "com.github.play2war" %% "play2-war-core" % "0.2"
    )
	
    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += (Resolver.file("Local Ivy Repository", file(Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)),
      resolvers += "Play2war plugins snapshot" at "http://repository-play-war.forge.cloudbees.com/snapshot/"
    )

}
