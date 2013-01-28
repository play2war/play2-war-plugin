package play.api.i18n

import scala.language.postfixOps
import play.api.i18n._
import play.api._
import play.core._
import java.io._
import scala.util.parsing.input._
import scala.util.parsing.combinator._
import scala.util.matching._
import scala.util.control.NonFatal

/**
 * High-level internationalisation API (not available yet).
 *
 * For example:
 * {{{
 * val msgString = Messages("items.found", items.size)
 * }}}
 */
object Messages {

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn�t defined
   */
  def apply(key: String, args: Any*)(implicit lang: Lang): String = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[MessagesPlugin].map(_.api.translate(key, args)).getOrElse(throw new Exception("this plugin was not registered or disabled"))
    }.getOrElse(noMatch(key, args))
  }

  /**
   * Retrieves all messages defined in this application.
   */
  def messages(implicit app: Application): Map[String, Map[String, String]] = {
    app.plugin[MessagesPlugin].map(_.api.messages).getOrElse(throw new Exception("this plugin was not registered or disabled"))
  }

  private def noMatch(key: String, args: Seq[Any]) = key

  private[i18n] case class Message(key: String, pattern: String, input: scalax.io.Input, sourceName: String) extends Positional

  /**
   * Message file Parser.
   */
  private[i18n] class MessagesParser(messageInput: scalax.io.Input, messageSourceName: String) extends RegexParsers {

    case class Comment(msg: String)

    override def skipWhitespace = false
    override val whiteSpace = """[ \t]+""".r

    def namedError[A](p: Parser[A], msg: String) = Parser[A] { i =>
      p(i) match {
        case Failure(_, in) => Failure(msg, in)
        case o => o
      }
    }

    def end = """\s*""".r
    def newLine = namedError((("\r"?) ~> "\n"), "End of line expected")
    def blankLine = ignoreWhiteSpace <~ newLine ^^ { case _ => Comment("") }
    def ignoreWhiteSpace = opt(whiteSpace)

    def comment = """#.*""".r ^^ { case s => Comment(s) }

    def messageKey = namedError("""[a-zA-Z0-9_.]+""".r, "Message key expected")

    def messagePattern = namedError(
      rep(
        """\""" ~> ("\r"?) ~> "\n" ^^ (_ => "") | // Ignore escaped end of lines \
          """\n""" ^^ (_ => "\n") | // Translate literal \n to real newline
          """\\""" ^^ (_ => """\""") | // Handle escaped \\
          """.""".r // Or any character
      ) ^^ { case chars => chars.mkString },
      "Message pattern expected"
    )

    def message = ignoreWhiteSpace ~ messageKey ~ (ignoreWhiteSpace ~ "=" ~ ignoreWhiteSpace) ~ messagePattern ^^ {
      case (_ ~ k ~ _ ~ v) => Messages.Message(k, v.trim, messageInput, messageSourceName)
    }

    def sentence = (comment | positioned(message)) <~ newLine

    def parser = phrase((sentence | blankLine *) <~ end) ^^ {
      case messages => messages.collect {
        case m @ Messages.Message(_, _, _, _) => m
      }
    }

    def parse = {
      parser(new CharSequenceReader(messageInput.string + "\n")) match {
        case Success(messages, _) => messages
        case NoSuccess(message, in) => {
          throw new PlayException.ExceptionSource("Configuration error", message) {
            def line = in.pos.line
            def position = in.pos.column - 1
            def input = messageInput.string
            def sourceName = messageSourceName
          }
        }
      }
    }

  }

}

/**
 * Play Plugin for internationalisation.
 */
class MessagesPlugin(app: Application) extends Plugin {

  import scala.collection.JavaConverters._

  import scalax.file._
  import scalax.io.JavaConverters._

  private def loadMessages(file: String): Map[String, String] = {
    Logger("play").info(s"[Override] MessagesPlugin:loadMessages: $file")
    
    Logger("play").info(s"[Override] MessagesPlugin:MessagesPlugin:loadMessages: classloader: $app.classloader")
    
    val resourceFiles = app.classloader.getResources(file).asScala
    
    Logger("play").info("[Override] MessagesPlugin:MessagesPlugin:loadMessages: resource files found: " + resourceFiles.mkString(", "))
    
    resourceFiles.toList.reverse.map { messageFile =>
      
      val input = messageFile.asInput
      val url = messageFile.toString
      Logger("play").info(s"[Override] MessagesPlugin:loadMessages: will parse file $url")
      
      new Messages.MessagesParser(input, url).parse.map { message =>
        message.key -> message.pattern
      }.toMap
    }.foldLeft(Map.empty[String, String]) { _ ++ _ }
  }

  private lazy val messages = {
    MessagesApi {
      Lang.availables(app).map(_.code).map { lang =>
        (lang, loadMessages("messages." + lang))
      }.toMap + ("default" -> loadMessages("messages"))
    }
  }

  /**
   * The underlying internationalisation API.
   */
  def api = messages

  /**
   * Loads all configuration and message files defined in the classpath.
   */
  override def onStart() {
    messages
  }

}
