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
    val listCookies: List[Cookie] = List.empty

    request.cookies.get("cookies1").map { cookie =>
      listCookies ++ Seq(cookie)
    }

    request.cookies.get("cookies2").map { cookie =>
      listCookies ++ Seq(cookie)
    }

    val mapCookies: Map[String, String] = listCookies.map {
      c => (c.name, c.value)
    }.toMap

    Ok(views.html.getCookies(mapCookies))
  }
}