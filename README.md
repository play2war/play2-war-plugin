# WAR Plugin for Play 2

    Current version: 0.1-SNAPSHOT

    Project-status: ALPHA

This project is a module for Play 2 to package your apps into standard WAR packages :)

----------

## Features matrix
<table style="text-align: center;">
  <tr>
	<th>Container</th>
    <th>Native Play 2</th>
	<th>Servlet 3.0</th>
	<th>Servlet 2.4/2.5</th>
  </tr>
  <tr>
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
    <td>Data sources</td>
	<td>Built-in<br/>([Bone CP](http://jolbox.com/))</td>
	<td colspan="2">Built-in ([Bone CP](http://jolbox.com/))<br/>External DS support : TBD</td>
  </tr>
  <tr>
    <td>WAR customization<br/>(web.xml, ...)</td>
	<td>N/A</td>
	<td>N/A</td>
	<td>TBD</td>  
  </tr>
</table>
----------

## Server compatibility matrix
<table style="text-align: center;">
  <tr>
	<th>Server</th>
    <th>Tomcat 6.x</th>
	<th>Tomcat 7.x</th>
	<th>JBoss 7.x</th>
    <th>Glassfish 3.x</th>
    <th>WAS x.x</th>
  </tr>
  <tr>
    <td>Standalone deployment</td>
	<td>TBD</td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td><img src="http://openclipart.org/image/800px/svg_to_png/161503/OK-1.png" height="20"></td>
	<td>TBD</td>
	<td>-</td>
  </tr>
  <tr>
    <td>PaaS</td>
	<td>TBD<br/>(Cloudbees, JElastic)</td>
	<td>TBD<br/>(JElastic)</td>
	<td>TBD<br/>(Cloudbees, Openshift)</td>
	<td>TBD<br/>(JElastic)</td>
	<td>-</td>
  </tr>
</table>
----------

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