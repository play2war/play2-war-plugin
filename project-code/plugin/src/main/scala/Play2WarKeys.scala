package sbt

trait Play2WarKeys {

  val servletVersion: SettingKey[String] =
      SettingKey[String](
        "servletVersion",
        "Servlet container version (2.4, 3.0)?")

}
object Play2WarKeys extends Play2WarKeys