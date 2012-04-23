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
import org.codehaus.cargo.container.deployable.WAR
import org.codehaus.cargo.container.property._
import org.codehaus.cargo.util.log._
import scala.collection.immutable.Map

object BasicTests {
  private val WAR_KEY = "war"

  private val ROOT_URL = "http://localhost:8080"
    
  private val TOMCAT_CONTAINER_URL = "http://apache.cict.fr/tomcat/tomcat-7/v7.0.27/bin/apache-tomcat-7.0.27.zip"
    
  private val TOMCAT_CONTAINER_NAME = "tomcat7x"
}

@RunWith(classOf[JUnitRunner])
class BasicTests extends FeatureSpec with GivenWhenThen with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfter {

  import BasicTests._

  var container: InstalledLocalContainer = null

  var webClient: WebClient = null

  override def beforeAll(configMap: Map[String, Any]) {

    val warPath = configMap.get(WAR_KEY).getOrElse("/home/damien/dev/play2-war-plugin/project-code/./../sample/target/a_warification-1.0-SNAPSHOT.war")

    println("WAR file to deploy: " + warPath)

    val containerUrl = TOMCAT_CONTAINER_URL
    val containerName = TOMCAT_CONTAINER_NAME

    println("Download container ...")
    val installer = new ZipURLInstaller(new URL(containerUrl))
    println("Download container done")

    println("Install container ...")
    installer.install
    println("Install container done")

    val configuration: LocalConfiguration = new DefaultConfigurationFactory().createConfiguration(
      containerName, ContainerType.INSTALLED, ConfigurationType.STANDALONE).asInstanceOf[LocalConfiguration]
    
    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.MEDIUM.getLevel());
    
    container =
      new DefaultContainerFactory().createContainer(
        containerName, ContainerType.INSTALLED, configuration).asInstanceOf[InstalledLocalContainer]

    println("Configure container")
    container.setHome(installer.getHome)
    container.setLogger(new SimpleLogger())

    val war = new WAR(warPath.toString)
    war.setContext("/")
    configuration.addDeployable(war)

    println("Start the container " + containerName)
    container.start
  }

  override def afterAll {
    println("Stop the container")
    Some(container).map(_ => container.stop)
  }

  before {
    webClient = new WebClient
    webClient.setJavaScriptEnabled(false)
  }

  after {
    webClient.closeAllWindows()
  }

  def givenWhenGet(given: String, path: String, root: String = ROOT_URL): Option[Page] = {

    this.given(given)
    val pageUrl = root + path

    when("page is loaded with GET method")
    info("Load page " + pageUrl)

    // webClient.getPage(pageUrl).asInstanceOf[Page]
    None
  }

  def thenCheckStatusCode(p: Option[Page], s: Int) {
    then("status code should be " + s)
    p.map {
      _.getWebResponse().getStatusCode() should be(s)
    }
  }

  val thenCheckOk = (p: Option[Page]) => thenCheckStatusCode(p, 200)
  val thenCheckRedirect = (p: Option[Page]) => thenCheckStatusCode(p, 301)
  val thenCheckNotFound = (p: Option[Page]) => thenCheckStatusCode(p, 404)

  val mapOfUrlOkStatus: Map[String, String] = Map(
    "home page" -> "/",
    "page in a sub directory" -> "/subdir",
    "page in a sub-sub directory" -> "/sub/subdir",
    "asset as image" -> "/assets/images/favicon.png")

  val mapOfUrlNotFoundStatus: Map[String, String] = Map(
    "not found 1" -> "/notfound",
    "not found 2" -> "/notfound.jpg",
    "not found 3" -> "/truc/muche/bidule")

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

}