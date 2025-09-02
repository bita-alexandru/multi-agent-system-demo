package alex.demo
package agents

import agents.Agent.{Command, CommandProps, getSystemPrompts}

import io.circe.*
import io.circe.parser.*
import cats.syntax.either.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import sttp.client4.quick.*

import java.io.FileReader
import scala.annotation.tailrec
import scala.util.Try

trait Agent:
  def apply(): Behavior[Command] =
    Behaviors.setup: context =>
      Behaviors.receiveMessage:
        case Command.Start(props) =>
          doStart(context, getSystemPrompts(context, Command.Start(props)), props)
        case Command.Next(props) =>
          doNext(context, getSystemPrompts(context, Command.Next(props)), props)
        case Command.Review(props) =>
          doReview(context, getSystemPrompts(context, Command.Review(props)), props)
        case Command.End(props) =>
          doEnd(context, getSystemPrompts(context, Command.End(props)), props)
        case Command.CallTool(props) =>
          doCallTool(context, getSystemPrompts(context, Command.CallTool(props)), props)
        case null => Behaviors.same
  end apply

  def doStart(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doNext(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doReview(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doEnd(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doCallTool(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]
end Agent

object Agent:
  private val geminiApiKey: String = dotenv.get("GEMINI_API_KEY")

  enum Command(val commandProps: CommandProps):
    case Start(props: CommandProps) extends Command(props)
    case Next(props: CommandProps) extends Command(props)
    case Review(props: CommandProps) extends Command(props)
    case End(props: CommandProps) extends Command(props)
    case CallTool(props: CommandProps) extends Command(props)

  sealed case class CommandProps(
    prompt: Option[String] = None,
    from: Option[ActorRef[Agent]] = None,
    contacts: Option[List[ActorRef[Agent]]] = None
  )

  private def getSystemPrompts(context: ActorContext[Command], command: Command): Option[String] =
    AgentConfigs.getAgentConfigByCommand(
      agentConfigs = AgentConfigs.agentConfigs,
      agentId = context.system.name,
      command = command
    )
  end getSystemPrompts

  private[agents] def askLlm(prompt: String): Option[String] =
    val requestBody =
      Json.obj:
        "contents" -> Json.arr:
          Json.obj:
            "parts" -> Json.arr:
              Json.obj:
                "text" -> Json.fromString(prompt)

    val request = quickRequest
      .post(uri"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")
      .header("Content-Type", "application/json")
      .header("X-goog-api-key", geminiApiKey)
      .body(requestBody.noSpaces)

    @tailrec
    def withRetries(retries: Int): Option[String] =
      if retries == 0 then
        None
      else
        val response = request.send()
        val maybeText = parse(response.body).toOption
          .flatMap:
            _.hcursor
              .downField("candidates").downArray
              .downField("content")
              .downField("parts").downArray
              .get[String]("text").toOption

        if maybeText.isDefined then
          maybeText
        else
          println(s"Error trying to get a response from Gemini, response body is ${response.body}")
          withRetries(retries - 1)
        end if
      end if
    end withRetries

    withRetries(2)
  end askLlm

  private[agents] def callTool(baseUrl: String, requestParams: Option[String], requestBody: Option[String]): Option[String] =
    val request = quickRequest
      .post(uri"$baseUrl")
      .header("Content-Type", "application/json")
      .body(requestBody.getOrElse(""))

    @tailrec
    def withRetries(retries: Int): Option[String] =
      if retries == 0 then
        None
      else
        val response = request.send()
        val maybeText = parse(response.body).toOption
          .flatMap:
            _.hcursor
              .downField("candidates").downArray
              .downField("content")
              .downField("parts").downArray
              .get[String]("text").toOption

        if maybeText.isDefined then
          maybeText
        else
          println(s"Error trying to get a response from Gemini, response body is ${response.body}")
          withRetries(retries - 1)
        end if
      end if
    end withRetries

    withRetries(2)
  end callTool
end Agent

object AgentConfigs:
  private given Decoder[SystemPrompts] = Decoder.forProduct5(
    "start",
    "next",
    "review",
    "end",
    "call-tool"
  )(SystemPrompts.apply)

  private[agents] sealed case class SystemPrompts(
    start: String,
    next: String,
    review: String,
    end: String,
    callTools: String
  )

  private type AgentConfig = Map[String, SystemPrompts]

  private val systemPromptsPath: String = "src/main/resources/system-prompts.yml"

  private[agents] val agentConfigs: AgentConfig = getAgentConfigs

  private def processJson(
    json: Either[ParsingFailure, Json]
  ): Either[Error, AgentConfig] =
    json
      .leftMap(err => err: Error)
      .flatMap(_.as[AgentConfig])
  end processJson

  private def readConfigs: Option[List[Either[Error, AgentConfig]]] =
    Try(new FileReader(systemPromptsPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match
      case Left(err) => None
      case Right(configs) =>
        Some(configs)
  end readConfigs

  private def getAgentConfigs: AgentConfig =
    readConfigs match
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, SystemPrompts])(_ ++ _)
      case None => Map.empty
  end getAgentConfigs

  private[agents] def getAgentConfigByCommand(agentConfigs: AgentConfig, agentId: String, command: Command): Option[String] =
    for
      config <- agentConfigs.get(agentId)
      prompt <- command match
        case Command.Start(_) => Some(config.start)
        case Command.Next(_) => Some(config.next)
        case Command.Review(_) => Some(config.review)
        case Command.End(_) => Some(config.end)
        case Command.CallTool(_) => Some(config.callTools)
    yield prompt
  end getAgentConfigByCommand

end AgentConfigs
