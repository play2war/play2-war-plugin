package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee._

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  // def echoMessage = TODO
  def echoMessage = WebSocket.using[String] { request =>
    // // Log events to the console
    // val in = Iteratee.foreach[String](println).mapDone { _ =>
    //   println("Disconnected")
    // }
    
    // // Send a single 'Hello!' message
    // val out = Enumerator("Hello!")
    
    // (in, out)
    val out = Enumerator.imperative[String] {
    }

    val in = Iteratee.foreach[String] { s =>
      println(s)
      out.push("Echoed: " + s)
    }.mapDone { _ =>
      println("Disconnected")
    }

   (in, out)
  }

  def echoStream = TODO
  
}