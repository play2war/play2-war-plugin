package play.core.server.servlet31

import play.core.server.servlet.HTTPHelpers
import javax.servlet.http.{ Cookie => ServletCookie }

private[servlet31] trait Helpers extends HTTPHelpers {

  override def getPlayCookie(c: ServletCookie): play.api.mvc.Cookie = play.api.mvc.Cookie(
    name = c.getName,
    value = c.getValue,
    maxAge = if (c.getMaxAge == -1) None else Some(c.getMaxAge),
    path = Option(c.getPath).getOrElse("/"),
    domain = Option(c.getDomain),
    secure = c.getSecure,
    httpOnly = c.isHttpOnly)

  override def getServletCookie(pCookie: play.api.mvc.Cookie): ServletCookie = {
    val sc = new ServletCookie(pCookie.name, pCookie.value)
    pCookie.domain.map(sc.setDomain)
    sc.setHttpOnly(pCookie.httpOnly)

    // conversion Play cookie to servlet cookie
    // |---------------------------------------|------------------|--------------------------|
    // | Use case                              | Play             | Servlet Container        |
    // |---------------------------------------|------------------|--------------------------|
    // | tell the browser to delete the cookie | Some(maxAge < 0) | maxAge = 0               |
    // | set a session cookie                  | None             | maxAge = -1 (or not set) |
    // | set a persistent cookie               | Some(maxAge > 0) | maxAge > 0               |
    // |---------------------------------------|------------------|--------------------------|
    pCookie.maxAge.map(ex => if (ex < 0) sc.setMaxAge(0) else sc.setMaxAge(ex))

    sc.setPath(pCookie.path)
    sc.setSecure(pCookie.secure)
    sc
  }
}
