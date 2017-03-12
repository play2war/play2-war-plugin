# Play2.5.4

## Environment
play    2.5.4
scala   2.11
tomcat  8.0
servlet 3.1

## Build
cd project-code
sbt clean
sbt -Dplay2war.sbt.scala211 -Dplay2.version=2.5.4 publishLocal

## Project Usage
- Modify {ProjectHome}/project/plugins.sbt

```
addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "1.5-beta1-SNAPSHOT")

```

- Modify {ProjectHome}/build.sbt

```
import com.github.play2war.plugin._

Play2WarPlugin.play2WarSettings
Play2WarKeys.servletVersion := "3.1"
Play2WarKeys.explodedJar := true

```

- Package

```
activator war

```

