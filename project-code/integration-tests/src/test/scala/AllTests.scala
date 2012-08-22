package com.github.play2war.plugin.it

import java.net.URL
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.matchers._
import org.scalatest._
import org.codehaus.cargo.container.InstalledLocalContainer
import org.codehaus.cargo.container.installer.ZipURLInstaller
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory
import org.codehaus.cargo.container.ContainerType
import org.codehaus.cargo.container.configuration.ConfigurationType
import org.codehaus.cargo.generic.DefaultContainerFactory
import org.codehaus.cargo.container.configuration.LocalConfiguration
import com.gargoylesoftware.htmlunit._
import com.gargoylesoftware.htmlunit.html._
import com.gargoylesoftware.htmlunit.util._
import org.codehaus.cargo.container.deployable.WAR
import org.codehaus.cargo.container.property._
import org.codehaus.cargo.util.log._
import scala.collection.immutable.{ Page => _, _ }
import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import java.io.File

object AbstractPlay2WarTests {

  private val ROOT_URL = "http://localhost:8080"
  
  // Milliseconds
  private val HTTP_TIMEOUT = 15000

}

abstract class AbstractPlay2WarTests extends FeatureSpec with GivenWhenThen with ShouldMatchers with CargoContainerManager with BeforeAndAfter {

  import AbstractPlay2WarTests._

  var webClient: WebClient = null

  var container: InstalledLocalContainer = null

  def getContainer = container

  def setContainer(container: InstalledLocalContainer) = this.container = container

  def containerUrl = ""

  def containerName = ""

  before {
    webClient = new WebClient
    webClient.setJavaScriptEnabled(false)
    webClient.setThrowExceptionOnFailingStatusCode(false)
    webClient.getCookieManager.setCookiesEnabled(true)
	webClient.setTimeout(HTTP_TIMEOUT)
    new SkipClockiFrameWrapper(webClient)
  }

  after {
    webClient.closeAllWindows
  }

  def sendRequest(pageUrl: String, method: String = "GET", parameters: Map[String, String] = Map.empty, howManyTimes: Int = 1): Option[Page] = {

    val strictMethod = HttpMethod.valueOf(method)
    val requestSettings = new WebRequest(new URL(pageUrl), strictMethod)

    val listParam = parameters.map {
      case (param, value) =>
        new NameValuePair(param, value)
    }.toList.asJava

    requestSettings.setRequestParameters(listParam)

    info("Page to load many times: " + howManyTimes)

    info("Load page: " + pageUrl)
    val result = Some(webClient.getPage(requestSettings))

    //    for (i <- 1 until howManyTimes) {
    //      info("Load page: " + pageUrl)
    //      webClient.getPage(requestSettings)
    //    }

    result
  }

  def givenWhenGet(given: String, path: String, when: String = "page is loaded with %s method", root: String = ROOT_URL, method: String = "GET", parameters: Map[String, String] = Map.empty, howManyTimes: Int = 1): Option[Page] = {

    this.given(given)
    val pageUrl = root + path

    this.when(when.format(method))

    sendRequest(pageUrl, method, parameters, howManyTimes)
  }

  def thenCheckStatusCode(p: Option[Page], s: Int) {
    then("status code should be " + s)
    p.map {
      _.getWebResponse.getStatusCode should be(s)
    }.getOrElse {
      fail("Page not found")
    }
  }

  val thenCheckOk = (p: Option[Page]) => thenCheckStatusCode(p, 200)
  val thenCheckRedirect = (p: Option[Page]) => thenCheckStatusCode(p, 301)
  val thenCheckNotFound = (p: Option[Page]) => thenCheckStatusCode(p, 404)
  val thenCheckInternalServerError = (p: Option[Page]) => thenCheckStatusCode(p, 500)

  val mapOfUrlOkStatus: Map[String, String] = Map(
    "home page" -> "/",
    "page in a sub directory" -> "/subdir",
    "page in a sub-sub directory" -> "/sub/subdir",
    "asset as image" -> "/assets/images/favicon.png")

  val mapOfUrlNotFoundStatus: Map[String, String] = Map(
    "not found 1" -> "/notfound",
    "not found 2" -> "/notfound.jpg",
    "not found 3" -> "/truc/muche/bidule")

  val mapOfUrlInternalServerError: Map[String, String] = Map(
    "not found 1" -> "/internalServerError")

  /*
   ******************
   ******************
   */

  feature("The container has a home page") {
    scenario("Load home page to init Play application") {
      val page = givenWhenGet("home page", "/")
      thenCheckOk(page)
    }
  }

  feature("The container must handle GET requests with OK status in response") {

    mapOfUrlOkStatus.foreach {
      case (k, v) => {

        scenario("Load " + k) {
          val page = givenWhenGet(k, v)
          thenCheckOk(page)
        }

      }
    }
  }

  feature("The container must handle GET requests with 'Not Found' status in response") {

    mapOfUrlNotFoundStatus.foreach {
      case (k, v) => {

        scenario("Load " + k) {
          val page = givenWhenGet(k, v)
          thenCheckNotFound(page)
        }

      }
    }
  }

  feature("The container must handle GET requests with 'Internal server error' status in response") {

    mapOfUrlInternalServerError.foreach {
      case (k, v) => {

        scenario("Load " + k) {
          val page = givenWhenGet(k, v)
          thenCheckInternalServerError(page)
        }

      }
    }
  }

  /*
   ******************
   ******************
   */

  feature("The container should query parameters") {

    scenario("Container reads GET parameters") {

      val page = givenWhenGet("a page", "/echo", parameters = Map("param1" -> "value1", "param2" -> "value2"))

      then("page body should contain parameters values")
      page.map { p =>
        p.getWebResponse.getContentAsString should (
          include("param1") and include("value1")
          and
          include("param2") and include("value2"))
      }.getOrElse {
        fail("Page not found")
      }
    }

    scenario("Container reads POST parameters") {

      val page = givenWhenGet("a page", "/echo", method = "POST", parameters = Map("param1" -> "value1", "param2" -> "value2"))

      then("page body should contain parameters values")
      page.map { p =>
        p.getWebResponse.getContentAsString should (
          include("param1") and include("value1")
          and
          include("param2") and include("value2"))
      }.getOrElse {
        fail("Page not found")
      }
    }

  }

  /*
   ******************
   ******************
   */

  feature("The container should handle cookies") {

    scenario("Container sets cookies") {

      givenWhenGet("a page", "/setCookies")

      then("response should contain cookies")

      val cookies = webClient.getCookieManager.getCookies.asScala
      cookies should have size (2)

      cookies.map {
        c => (c.getName, c.getValue)
      }.toMap should (
        contain("cookie1" -> "value1")
        and contain("cookie2" -> "value2"))
    }

    scenario("Container gets cookies") {

      // Load cookies
      webClient.getPage(ROOT_URL + "/setCookies")

      val page = givenWhenGet("a page", "/getCookies", "client sends cookies")

      then("page body should contain cookies values")
      page.map { p =>
        p.getWebResponse.getContentAsString should (
          include("cookie1") and include("value1")
          and
          include("cookie2") and include("value2"))
      }.getOrElse {
        fail("Page not found")
      }
    }
  }

  /*
   ******************
   ******************
   */

  feature("The container must handle GET requests with 'Redirect' status in response") {

    scenario("container sends redirect") {

      val page = givenWhenGet("a page which will redirect", "/redirect")

      then("response page should be a redirected page")

      page.map { p =>
        p.getWebResponse.getContentAsString should include("redirect landing")
      }.getOrElse {
        fail("Page not found")
      }
    }
  }

  /*
   ******************
   ******************
   */

  def downloadBigContent(name: String, url: String, maxRange: Int, header: String, expectedHeaderValue: String, expectedSize: Int, howManyTimes: Int = 1) = {
    val page = givenWhenGet("a page which sends " + name, url, parameters = Map("maxRange" -> maxRange.toString), howManyTimes = howManyTimes)

    then("response page should be downloaded")

    page.map { p =>

      p.getWebResponse.getStatusCode should be(200)

      and("have a specified " + header)
      info("Detected " + header + ": " + p.getWebResponse.getResponseHeaderValue(header))
      if (expectedHeaderValue.isEmpty) {
        p.getWebResponse.getResponseHeaderValue(header) should be(expectedSize.toString)
      } else {
        p.getWebResponse.getResponseHeaderValue(header) should be(expectedHeaderValue)
      }

      and("have a specified size")
      p.getWebResponse.getContentAsStream.available should be(expectedSize)

    }.getOrElse {
      fail("Page not found")
    }
  }

  val mapOfMaxRangeExpectedSize: Map[Int, Int] = Map(
    100000 -> 588890 //
    , 300000 -> 1988890 //
    //, 500000 -> 3388890 // Ca craque avec content-length, mais ca a l'air de passer avec Transfert-encoding
    //, 900000 -> 
    )

  val seqTupleBigContent = Seq(
    // (page name, page url, expected header)
    ("big content", "/bigContent", "Content-length", ""),
    ("big chunked content", "/chunkedBigContent", "Transfer-Encoding", "chunked"))

  feature("The container must handle GET requests of big content") {

    seqTupleBigContent.foreach {
      case (name, url, header, expectedHeaderValue) => {

        mapOfMaxRangeExpectedSize.foreach {
          case (maxRange, expectedSize) => {

            scenario("container sends big files (" + expectedSize + " bytes expected with " + header + " header") {

              downloadBigContent(name, url, maxRange, header, expectedHeaderValue, expectedSize)

            }
          }
        }
      }
    }
  }

  val howManyTimes = 10

  feature("The container must handle GET requests of big content many times") {

    seqTupleBigContent.foreach {
      case (name, url, header, expectedHeaderValue) => {

        val (maxRange, expectedSize) = mapOfMaxRangeExpectedSize.head

        scenario("container sends big files (" + expectedSize + " bytes expected with " + header + " header") {

          downloadBigContent(name, url, maxRange, header, expectedHeaderValue, expectedSize, howManyTimes)

        }
      }
    }
  }

  /*
   ******************
   ******************
   */

  feature("The container must handle POST requests with 'multipart/form-data' enctype") {
    
    // 2 routes to test
    List("/upload", "/upload2").foreach {
      case (route) => {

        scenario("container sends an image to " + route) {

          this.given("a form which sends a image to " + route)
          val pageUrl = ROOT_URL + route

          this.when("image is uploaded")
          info("Load page " + pageUrl)

          val strictMethod = HttpMethod.valueOf("POST")
          val requestSettings = new WebRequest(new URL(pageUrl), strictMethod)
          requestSettings.setEncodingType(FormEncodingType.MULTIPART);

          import java.io._

          val imageName = "play-logo.png"
          val image = new File(getClass.getResource("/" + imageName).toURI)

          val listParam: List[NameValuePair] = List(new KeyDataPair("uploadedFile", image, "image/png", "utf-8"))

          requestSettings.setRequestParameters(listParam.asJava)

          val page: Some[Page] = Some(webClient.getPage(requestSettings))

          then("response page should contains image name")

          page.map { p =>
            p.getWebResponse.getContentAsString should include(imageName)
          }.getOrElse {
            fail("Page not found")
          }
        }
      }
    }
  }
}

abstract class AbstractTomcat7x extends AbstractPlay2WarTests with Servlet30Container {
  def tomcatVersion() = "Version to override"
  override def containerUrl = "http://archive.apache.org/dist/tomcat/tomcat-7/v" + tomcatVersion + "/bin/apache-tomcat-"+ tomcatVersion + ".tar.gz"
  override def containerName = "tomcat7x"
}

/*@RunWith(classOf[JUnitRunner])
class Tomcat7027Tests extends AbstractTomcat7x {
  override def tomcatVersion = "7.0.27"
}*/

@RunWith(classOf[JUnitRunner])
class Tomcat6xTests extends AbstractPlay2WarTests with Servlet25Container {
  override def containerUrl = "http://archive.apache.org/dist/tomcat/tomcat-6/v6.0.35/bin/apache-tomcat-6.0.35.tar.gz"
  override def containerName = "tomcat6x"
}

/*@RunWith(classOf[JUnitRunner])
class Tomcat7027Tests extends AbstractTomcat7x {
  override def tomcatVersion = "7.0.27"
}*/

@RunWith(classOf[JUnitRunner])
class Jetty7xTests extends AbstractPlay2WarTests with Servlet25Container {
  override def containerUrl = "http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/7.6.5.v20120716/jetty-distribution-7.6.5.v20120716.tar.gz"
  override def containerName = "jetty7x"
}

@RunWith(classOf[JUnitRunner])
class Tomcat7029Tests extends AbstractTomcat7x {
  override def tomcatVersion = "7.0.29"
}

@RunWith(classOf[JUnitRunner])
class Jetty8xTests extends AbstractPlay2WarTests with Servlet30Container {
  override def containerUrl = "http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/8.1.3.v20120416/jetty-distribution-8.1.3.v20120416.tar.gz"
  override def containerName = "jetty8x"
}

// Doesn't work yet : deployment of sample war fails : Command deploy requires an operand of type class java.io.File
//@RunWith(classOf[JUnitRunner])
//class Glassfish3xTests extends AbstractPlay2WarTests {
//  override def containerUrl = "http://download.java.net/glassfish/3.1.2/release/glassfish-3.1.2.zip"
//  override def containerName = "glassfish3x"
//}

//@RunWith(classOf[JUnitRunner])
//class JOnAS5xTests extends AbstractPlay2WarTests {
//  override def containerUrl = "http://repo1.maven.org/maven2/org/ow2/jonas/assemblies/profiles/jonas-full/5.2.3/jonas-full-5.2.3-bin.tar.gz"
//  override def containerName = "jonas5x"
//}

//@RunWith(classOf[JUnitRunner])
//class JBoss7xTests extends AbstractPlay2WarTests {
//  override def containerUrl = "http://download.jboss.org/jbossas/7.0/jboss-as-7.0.2.Final/jboss-as-7.0.2.Final.zip"
//  override def containerName = "jboss7x"
//}

//@RunWith(classOf[JUnitRunner])
//class JBoss71xTests extends AbstractPlay2WarTests {
//  override def containerUrl = "http://download.jboss.org/jbossas/7.1/jboss-as-7.1.1.Final/jboss-as-7.1.1.Final.zip"
//  override def containerName = "jboss71x"
//}
