# WAR Plugin for Play framework 2.0

    Current version: 0.2-SNAPSHOT

    Project-status: ALPHA

This project is a module for Play framework 2 to package your apps into standard WAR packages.

Live demo : http://play2war.jelastic.dogado.eu/

## Features matrix
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
    <br/>Eg: http://...:80/</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20" title="Always deployed at root context"></td>
	<td colspan="2"><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.pngg" title="WAR package must be deployed at root context" height="20"></td>
  </tr>
  <tr>
    <td>Non root context path
    <br/>Eg: http://...:80/myAppContext</td>
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

## Server compatibility matrix
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
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161515/OK-2.png" height="20" title="Doesn't work">
		<br/>(Cloudbees)<br/>See <a href="https://github.com/dlecan/play2-war-plugin/issues/15">#15</a>
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

## Issues
Please file issues here: https://github.com/dlecan/play2-war-plugin/issues

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
