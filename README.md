# WAR Plugin for Play framework 2.0

    Current version: 0.3.1

    Project-status: ALPHA

This project is a module for Play framework 2 to package your apps into standard WAR packages.

As of version 0.3.1, it is only compatible with Play2 **2.0.1**.

Live demos :

- Tomcat7@Jelastic : http://play2war.jelastic.dogado.eu/
- JBoss7@Cloudbees : http://servlet30.play-war.cloudbees.net/

## Features
<table>
  <tr>
	<th rowspan="2" colspan="2">Feature</th>
    <th rowspan="2">Native Play 2</th>
	<th colspan="2">Servlet engine</th>
  </tr>
  <tr>
	<th>3.0</th>
	<th>2.4/2.5</th>
  </tr>
  <tr>
	<td colspan="2">Available ?</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
    <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
    <td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>
  </tr>
  <tr>
	<td rowspan="4">HTTP</td>
    <td>Asynchronous request<br/>processing</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>
  </tr>
  <tr>
    <td>GET/POST<br/>HTTP 1.0/1.1</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td>TBD</td>
  </tr>
  <tr>
    <td>Chunked response</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td>TBD</td>
	<td>TBD</td>
  </tr>
  <tr>
    <td>Web Socket</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>  
  </tr>
  <tr>
	<td rowspan="4">Container</td>
    <td>Data sources</td>
	<td>Built-in<br/>(<a href="http://jolbox.com/">Bone CP</a>)</td>
	<td colspan="2">Built-in (<a href="http://jolbox.com/">Bone CP</a>)<br/>External DS support : TBD</td>
  </tr>
  <tr>
    <td>Root context path
    <br/>Eg: http://local/</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20" title="Always deployed at root context"></td>
	<td colspan="2"><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.pngg" title="WAR package must be deployed at root context" height="20"></td>
  </tr>
  <tr>
    <td>Non root context path
    <br/>Eg: http://local/myAppContext</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20" title="Always deployed at root context"></td>
	<td colspan="2"><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" title="WAR package must be deployed at root context" height="20"><br/>TBD ?</td>
  </tr>
  <tr>
    <td>WAR customization<br/>(web.xml, ...)</td>
	<td>N/A</td>
	<td>TBD</td>
	<td>TBD</td>  
  </tr>
</table>

## Server compatibility
<table>
  <tr>
	<th>Servlet engine</th>
    <th>Server</th>
	<th>Standalone deployment</th>
	<th>PaaS</th>
  </tr>
  <tr>
	<td rowspan="4">Servlet 3.0</td>
	<td>Tomcat 7</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"> 
		<a href="http://play2war.jelastic.dogado.eu/" title="Play 2 WAR demo hosted at Jelastic PaaS provider">Demo</a> @Jelastic
	</td>
  </tr>
  <tr>
	<td>JBoss 7.0</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20">
		<a href="http://servlet30.play-war.cloudbees.net/" title="Play 2 WAR demo hosted at Cloudbees PaaS provider">Demo</a> @Cloudbees
		<br/><a href="https://github.com/dlecan/play2-war-plugin/wiki/FAQ#jboss7-deployment-at-cloudbees">Need extra configuration</a> when deploying
	</td>
  </tr>
  <tr>
	<td>JBoss 7.1</td>
	<td>TBD</td>
	<td>TBD<br/>(Openshift)</td>
  </tr>
  <tr>
	<td>Glassfish 3</td>
	<td>TBD</td>
	<td>TBD<br/>(Jelastic)</td>
  </tr>
  <tr>
	<td rowspan="2">Servlet 2.4/2.5</td>
	<td>Tomcat 6</td>
	<td>TBD</td>
	<td>TBD<br/>(Cloudbees)</td>
  </tr>
  <tr>
	<td>Jetty 6</td>
	<td>TBD</td>
	<td>TBD<br/>(Jelastic)</td>
  </tr>
</table>

## Usage

In the next descriptions, APP_HOME is the root of your Play 2.0 application you want to package as a WAR file.

### Add play2war plugin

In ``APP_HOME/project/plugins.sbt``, add:

```scala
resolvers += "Play2war plugins release" at "http://repository-play-war.forge.cloudbees.com/release/"

addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "0.3.1")
```

### Add play2war runtime

In ``APP_HOME/project/Build.scala``, modify ``appDependencies`` and ``main`` values to add:

```scala
val appDependencies = Seq(
  ...
  "com.github.play2war" %% "play2-war-core" % "0.3.1"
  ...
)

val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
  ...
  resolvers += "Play2war plugins release" at "http://repository-play-war.forge.cloudbees.com/release/"
  ...
)
```

### Configure logging

You probably need to override default Play 2.0 logging configuration because :

- An external file will be written in ``$USER_HOME/logs/...``

- STDOUT appender pattern can be improved

Create a file ``APP_HOME/conf/logger.xml`` with the following content :

```xml
<configuration>
    
  <conversionRule conversionWord="coloredLevel" converterClass="play.api.Logger$ColoredLevel" />

  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%date - [%level] - from %logger in %thread %n%message%n%xException%n</pattern>
    </encoder>
  </appender>
  
  <logger name="play" level="INFO" />
  <logger name="application" level="INFO" />
  
  <!-- Off these ones as they are annoying, and anyway we manage configuration ourself -->
  <logger name="com.avaje.ebean.config.PropertyMapLoader" level="OFF" />
  <logger name="com.avaje.ebeaninternal.server.core.XmlConfigLoader" level="OFF" />
  <logger name="com.avaje.ebeaninternal.server.lib.BackgroundThread" level="OFF" />

  <root level="ERROR">
    <appender-ref ref="STDOUT" />
  </root>
```
  
</configuration>

## Packaging

If Play runtime is available, run

    play war

Your WAR package will be available in ``APP_HOME/target/<MY_PROJECT>_version.war``

## Upload or deploy your WAR file

Upload or deploy your WAR file to your favorite Application Server if compatible (see <a href="#server-compatibility">Compatibility matrix above</a>).

## FAQ

See [FAQ](/dlecan/play2-war-plugin/wiki/FAQ).

## Issues

Please file issues here: https://github.com/dlecan/play2-war-plugin/issues.

## Continous integration

Watch it in action here : [https://play-war.ci.cloudbees.com/](https://play-war.ci.cloudbees.com/).

## How to help ?

Discover [how you can help the project](/dlecan/play2-war-plugin/wiki/How-to-help).

## Licence

This software is licensed under the Apache 2 license, quoted below.

Copyright 2012 Damien Lecan (http://www.dlecan.com).

Licensed under the Apache License, Version 2.0 (the "License"); you 
may not use this project except in compliance with the License. You 
may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
