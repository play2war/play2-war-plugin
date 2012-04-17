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

@RunWith(classOf[JUnitRunner])
class BasicTests extends FeatureSpec with GivenWhenThen with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfter {

  private val WAR_KEY = "war"

  var container: InstalledLocalContainer = null

  var webClient: WebClient = null

  override def beforeAll(configMap: Map[String, Any]) {

    val warPath = configMap.get(WAR_KEY).getOrElse("/home/damien/dev/play2-war-plugin/project-code/./../sample/target/a_warification-1.0-SNAPSHOT.war")

    println("WAR file to deploy: " + warPath)

    val containerUrl = "http://apache.cict.fr/tomcat/tomcat-7/v7.0.27/bin/apache-tomcat-7.0.27.zip"
    val containerName = "tomcat7x"

    println("Download container ...")
    val installer = new ZipURLInstaller(new URL(containerUrl))
    println("Download container done")

    println("Install container ...")
    installer.install
    println("Install container done")

    val configuration: LocalConfiguration = new DefaultConfigurationFactory().createConfiguration(
      containerName, ContainerType.INSTALLED, ConfigurationType.STANDALONE).asInstanceOf[LocalConfiguration]

    container =
      new DefaultContainerFactory().createContainer(
        containerName, ContainerType.INSTALLED, configuration).asInstanceOf[InstalledLocalContainer]

    println("Configure container home")
    container.setHome(installer.getHome)

    val war = new WAR(warPath.toString)
    war.setContext("/")
    configuration.addDeployable(war);

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
    webClient.closeAllWindows();
  }

  feature("The container can handle GET and POST requests") {
    scenario("Handle GET request on HTML page") {

      given("a page")
      val pageUrl = "http://localhost:8080/"

      when("page is loaded with GET method")
      val page = webClient.getPage(pageUrl).asInstanceOf[HtmlPage];

      then("status code should be 200")
      page.getWebResponse().getStatusCode() should be(200)
    }

    scenario("Handle GET request on Assets") {

      given("an image")
      val pageUrl = "http://localhost:8080/assets/images/favicon.png"

      when("image is loaded with GET method")
      val page = webClient.getPage(pageUrl).asInstanceOf[Page];

      then("status code should be 200")
      page.getWebResponse().getStatusCode() should be(200)

    }
  }
}