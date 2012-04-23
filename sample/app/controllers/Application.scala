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
}