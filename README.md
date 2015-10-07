# WAR Plugin for Play framework 2.x

    Current versions:
        Play 2.2.x          : 1.2.1
        Play 2.3.0 -> 2.3.1 : 1.3-beta1 (Scala 2.10 & 2.11)
        Play 2.3.2+         : 1.3-beta3 (Scala 2.10 & 2.11)
        Play 2.4.0+         : 1.4-beta1 (Scala 2.10 & 2.11)

    Project-status: STABLE

[![Build Status](https://play-war.ci.cloudbees.com/job/Play_2_War_Run_integration_tests_-_Play_22x/badge/icon)](https://play-war.ci.cloudbees.com/job/Play_2_War_Run_integration_tests_-_Play_22x/)

See also [archived versions](#versions-not-supported-anymore).

This project is a module for Play framework 2 to package your apps into standard WAR packages. It can be used with **Servlet 3.1, 3.0 and 2.5 containers** (Tomcat 6/7/8, Jetty 7/8/9, JBoss 5/6/7/8, ...)

Why choosing WAR packaging when native Play 2 is a better deployment model (features and performances) ?
- Ops don't want to change their deployment model and still want to use WAR in your company
- SSL is available, easy to configure and well documented on JBoss, Tomcat, ... when SSL is newer on Play 2.1
- You need to add extra Servlet filters specific to your company (to handle SSO, ...)

You can trust this plugin because it is [heavily tested](https://play-war.ci.cloudbees.com/job/Play_2_War_Run_integration_tests_-_Play_23x/lastCompletedBuild/testReport/), with hundreds of integration tests run on :
- several open-source application servers (Tomcat 6/7/8, Jetty 7/8/9)
- *all versions* of Play Framework 2.x

Other references built with Play 2 and Play2War:
 - [Factile](http://factile.net/) (Survey platform)

## What's new ?

See [releases and changelog](https://github.com/play2war/play2-war-plugin/releases/).

## Features
<table>
  <tr>
  <th rowspan="2" colspan="2">Features</th>
  <th rowspan="2">Native Play 2</th>
  <th colspan="3">Servlet engine</th>
  </tr>
  <tr>
  <th>3.1</th>
  <th>3.0</th>
  <th>2.4/2.5</th>
  </tr>
  <tr>
  <td colspan="2">Availability</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"><br/>P2W 1.2+</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  </tr>
  <tr>
  <td colspan="2">Performances</td>
  <td>+++</td>
  <td>++</td>
  <td>+</td>
  <td>-</td>
  </tr>
  <tr>
  <td rowspan="2">HTTP</td>
  <td>Asynchronous request<br/>processing</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>
  </tr>
  <tr>
  <td>Web Socket</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>Not available yet</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20"></td>  
  </tr>
  <tr>
  <td rowspan="3">Container</td>
  <td>Data sources</td>
  <td>Built-in<br/>(<a href="http://jolbox.com/">Bone CP</a>)</td>
  <td colspan="3">Built-in (<a href="http://jolbox.com/">Bone CP</a>)<br/>Support external DataSource without JTA</td>
  </tr>
  <tr>
    <td>Applications deployed at root context
        <br/>Eg: http://myhost/</td>
    <td colspan="4">
      <p align="center">
        <img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20" title="Any Play versions">
      </p>
    </td>
  </tr>
  <tr>
    <td>Applications deployed at sub-context
        <br/>Eg: http://myhost/mySubAppContext</td>
    <td colspan="4">
      <p align="center">
        Play 2.0.x : <img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20" title="Always deployed at root context">
        <br/>Play 2.1.x and more : <img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20">
      </p>
    </td>
  </tr>
</table>

## Server compatibility
<table>
  <tr>
  <th>Servlet engine</th>
        <th>Server</th>
  <th>In your company</th>
  <th>Public PaaS</th>
  </tr>
  <tr>
  <td rowspan="4">Servlet 3.1</td>
  <td>Tomcat 8</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>TBD</td>
  </tr>
  <tr>
  <td>Jetty 9.1</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>TBD</td>
  </tr>
  <tr>
  <td>JBoss/Wildfly 8</td>
  <td>TBD</td>
  <td>TBD</td>
  </tr>
  <tr>
  <td>Glassfish 4</td>
  <td>TBD</td>
  <td>TBD</td>
  </tr>
  <tr>
  <td rowspan="8">Servlet 3.0</td>
  <td>Tomcat 7</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  </tr>
  <tr>
  <td>Jetty 8</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  </tr>
  <tr>
  <td>Jetty 9.0</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>TBD</td>
  </tr>
  <tr>
  <td>JBoss 7.0</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20">
    <a href="http://servlet30.play-war.cloudbees.net/" title="Play 2 WAR demo hosted at Cloudbees PaaS provider">Demo</a> @Cloudbees
    <br/><a href="https://github.com/play2war/play2-war-plugin/wiki/FAQ#jboss7-deployment-at-cloudbees">Need extra configuration</a> when deploying
  </td>
  </tr>
  <tr>
  <td>JBoss 7.1</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>TBD<br/>(Openshift)</td>
  </tr>
  <tr>
  <td>Glassfish 3</td>
  <td>TBD</td>
  <td>TBD</td>
  </tr>
  <tr>
  <td>Websphere Community Edition 3.0</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>-</td>
  </tr>
  </tr>
  <tr>
  <td>Websphere 8.5 Liberty Profile</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>-</td>
  </tr>
  <tr>
  <td rowspan="2">Servlet 2.5</td>
  <td>Tomcat 6</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>-</td>
  </tr>
  <tr>
  <td>Jetty 7</td>
  <td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
  <td>-</td>
  </tr>
</table>

The plugin may work on others containers, such as Weblogic (not tested yet).

## Configuration

See [Configuration](https://github.com/play2war/play2-war-plugin/wiki/Configuration).

## Usage

See [Usage](https://github.com/play2war/play2-war-plugin/wiki/Usage).

## Deployment

See [Deployment](https://github.com/play2war/play2-war-plugin/wiki/Deployment).

## FAQ

See [FAQ](https://github.com/play2war/play2-war-plugin/wiki/FAQ).

## Issues

Please file issues here: https://github.com/play2war/play2-war-plugin/issues.

## Continous integration

Watch it in action here : [https://play-war.ci.cloudbees.com/](https://play-war.ci.cloudbees.com/).

## How to help ?

Discover [how you can help the project](https://github.com/play2war/play2-war-plugin/wiki/How-to-help).

## Contributors

[Ivan Meredith](https://github.com/hadashi), [Rossi Oddet](https://github.com/roddet), [Sam Spycher](https://github.com/samspycher), [Naoki Takezoe](https://github.com/takezoe), [Quinn Slack](https://github.com/sqs), [Eugene Platonov](https://github.com/jozic), [László Zsolt Kustra](https://github.com/kustra), [Yann Simon](https://github.com/yanns).

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

## Built by CloudBees
<img src="http://web-static-cloudfront.s3.amazonaws.com/images/badges/BuiltOnDEV.png"/>

[![githalytics.com alpha](https://cruel-carlota.pagodabox.com/bf76ccaad18897abc9d723474033290c "githalytics.com")](http://githalytics.com/dlecan/play2-war-plugin)

## Versions not supported anymore

    Play 2.0 -> 2.0.4   : 0.8.1
    Play 2.0.5+         : 0.8.2
    Play 2.1 -> 2.1.3   : 1.0.2
    Play 2.1.4+         : 1.1.1
