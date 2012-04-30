//

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
import scala.collection.immutable.Map
import scala.collection.JavaConverters._

trait CargoContainerManager extends BeforeAndAfterAll {
  self: Suite =>

  private val WAR_KEY = "war"

  def getContainer: InstalledLocalContainer

  def setContainer(container: InstalledLocalContainer): Unit

  def containerUrl: String

  def containerName: String

  def context = "/"

  abstract override def beforeAll(configMap: Map[String, Any]) {

    val warPath = configMap.get(WAR_KEY).getOrElse("/home/damien/dev/play2-war-plugin/project-code/./../sample/target/a_warification-1.0-SNAPSHOT.war")

    println("WAR file to deploy: " + warPath)

    println("Download container " + containerName + " from " + containerUrl + " ...")
    val installer = new ZipURLInstaller(new URL(containerUrl))
    println("Download container done")

    println("Install container ...")
    installer.install
    println("Install container done")

    val configuration: LocalConfiguration = new DefaultConfigurationFactory().createConfiguration(
      containerName, ContainerType.INSTALLED, ConfigurationType.STANDALONE).asInstanceOf[LocalConfiguration]

    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.MEDIUM.getLevel);

    val container =
      new DefaultContainerFactory().createContainer(
        containerName, ContainerType.INSTALLED, configuration).asInstanceOf[InstalledLocalContainer]

    println("Configure container")
    container.setHome(installer.getHome)
    container.setLogger(new SimpleLogger)

    val war = new WAR(warPath.toString)
    war.setContext(context)
    configuration.addDeployable(war)

    println("Start the container " + containerName)
    setContainer(container)
    container.start
  }

  abstract override def afterAll {
    println("Stop the container")
    Some(getContainer).map {
      _.stop
    }
  }

}

object AbstractPlay2WarTests {

  private val ROOT_URL = "http://localhost:8080"

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
  }

  after {
    webClient.closeAllWindows
  }

  def sendRequest(pageUrl: String, method: String = "GET", parameters: Map[String, String] = Map.empty): Option[Page] = {

    val strictMethod = HttpMethod.valueOf(method)
    val requestSettings = new WebRequest(new URL(pageUrl), strictMethod)

    val listParam = parameters.map {
      case (param, value) =>
        new NameValuePair(param, value)
    }.toList.asJava

    requestSettings.setRequestParameters(listParam)

    Some(webClient.getPage(requestSettings))
  }

  def givenWhenGet(given: String, path: String, when: String = "page is loaded with %s method", root: String = ROOT_URL, method: String = "GET", parameters: Map[String, String] = Map.empty): Option[Page] = {

    this.given(given)
    val pageUrl = root + path

    this.when(when.format(method))
    info("Load page " + pageUrl)

    sendRequest(pageUrl, method, parameters)
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

  feature("The container must handle GET requests of big content") {

    scenario("container sends big files with Content-length header") {

      val page = givenWhenGet("a page which sends big content", "/bigContent")

      then("response page should be downloaded")
      
      page.map { p =>
        p.getWebResponse.getStatusCode should be(200)

        and("have a specified Content-length")
        p.getWebResponse.getResponseHeaderValue("Content-length") should be((2 * 1024 * 1024).toString)
        
        and("have a specified size")
        p.getWebResponse.getContentAsStream.available should be(2 * 1024 * 1024)
        
      }.getOrElse {
        fail("Page not found")
      }
    }

    scenario("container sends big files with chunked Transfer-Encoding") {

      val page = givenWhenGet("a page which sends big content", "/chunkedBigContent")

      then("response page should be downloaded")
      
      page.map { p =>
        p.getWebResponse.getStatusCode should be(200)

        and("have a specified the Transfer-Encoding header")
        p.getWebResponse.getResponseHeaderValue("Transfer-Encoding") should be("chunked")
        
        and("have a specified size")
        p.getWebResponse.getContentAsStream.available should be(10485760)
      }.getOrElse {
        fail("Page not found")
      }
    }
  }
}

@RunWith(classOf[JUnitRunner])
class Tomcat7xTests extends AbstractPlay2WarTests {
  override def containerUrl = "http://apache.cict.fr/tomcat/tomcat-7/v7.0.27/bin/apache-tomcat-7.0.27.zip"
  override def containerName = "tomcat7x"
}

//@RunWith(classOf[JUnitRunner])
//class Jetty8xTests extends AbstractPlay2WarTests {
//  override def containerUrl = "http://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/8.1.3.v20120416/jetty-distribution-8.1.3.v20120416.tar.gz"
//  override def containerName = "jetty8x"
//}

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
