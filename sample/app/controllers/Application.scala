package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def setCookies = Action {
    val result = Ok(views.html.index("Cookies have been set."))

    val cookie1 = new Cookie("cookie1", "value1", 3600)
    val cookie2 = new Cookie("cookie2", "value2", 3600)

    result.withCookies(cookie1).withCookies(cookie2)
  }

  def getCookies = Action { request =>
    var listCookies = List[Cookie]()
    
    request.cookies.get("cookie1").map { cookie =>
        listCookies ++= Seq(cookie)
    }

    request.cookies.get("cookie2").map { cookie =>
      listCookies ++= Seq(cookie)
    }

    val mapCookies: Map[String, String] = listCookies.map {
      c => (c.name, c.value)
    }.toMap
    
    println("Cookies sent to view:" + mapCookies)

    Ok(views.html.getCookies(mapCookies))
  }

  def redirectLanding = Action {
    Ok(views.html.redirectLanding())
  }
  
  def redirect = Action {
    Redirect(routes.Application.redirectLanding) 
  }
  
  def internalServerError = Action {  request =>
    throw new RuntimeException("This a desired exception in order to test exception interception")
  }
}