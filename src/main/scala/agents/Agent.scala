package alex.demo
package agents

import agents.Agent.{Command, CommandProps, getSystemPrompts}

import io.circe.*
import io.circe.parser.*
import cats.syntax.either.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import sttp.client4.Request
import sttp.client4.quick.*

import java.io.FileReader
import scala.annotation.tailrec
import scala.util.Try

trait Agent:
  def apply(): Behavior[Command] =
    Behaviors.setup: context =>
      given ActorContext[Command] = context

      Behaviors.receiveMessage:
        case Command.Start(props) =>
          doStart(getSystemPrompts(Command.Start(props)), props)
        case Command.Next(props) =>
          doNext(getSystemPrompts(Command.Next(props)), props)
        case Command.Review(props) =>
          doReview(getSystemPrompts(Command.Review(props)), props)
        case Command.End(props) =>
          doEnd(getSystemPrompts(Command.End(props)), props)
        case Command.CallTool(props) =>
          doCallTool(getSystemPrompts(Command.CallTool(props)), props)
        case null => Behaviors.same
  end apply

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doNext(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doReview(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doEnd(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doCallTool(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]
end Agent

object Agent:
  private val geminiApiKey: String = dotenv.get("GEMINI_API_KEY")
  private val geminiBaseUrl: String = dotenv.get("GEMINI_BASE_URL")

  enum Label(val value: String):
    case User extends Label("user")
    case Supervisor extends Label("supervisor-agent")
    case Worker extends Label("worker-agent")
    case Unknown extends Label("unknown")
    case ProfileWorker extends Label("profileWorker")
    case DocsWorker extends Label("docsWorker")
  end Label

  enum Command(val commandProps: CommandProps = CommandProps()):
    case Start(props: CommandProps = CommandProps()) extends Command(props)
    case Next(props: CommandProps = CommandProps()) extends Command(props)
    case Review(props: CommandProps = CommandProps()) extends Command(props)
    case End(props: CommandProps = CommandProps()) extends Command(props)
    case CallTool(props: CommandProps = CommandProps()) extends Command(props)
  end Command

  sealed case class CommandProps(
    prompt: Option[String] = None,
    input: Option[String] = None,
    from: Option[ActorRef[Command]] = None
  )

  private def getSystemPrompts(command: Command)(using context: ActorContext[Command]): Option[String] =
    AgentsBehaviours.getbehaviourByCommand(
      behaviours = AgentsBehaviours.behaviours,
      agentLabel = getAgentLabelFromName(context.self.path.name),
      command = command
    )
  end getSystemPrompts

  private def getAgentLabelFromName(name: String): String =
    name match
      case Label.User.value => Label.Supervisor.value
      // @formatter:off
      case s"${Label.Worker.value}-$id" => Label.Worker.value
      // @formatter:on
      case _ => Label.Worker.value
  end getAgentLabelFromName

  private[agents] def getAgentIdFromName(name: String): String =
    name match
      case Label.User.value => "supervisor"
      // @formatter:off
      case s"${Label.Worker.value}-$id" => id
      // @formatter:on
      case _ => Label.Unknown.value
  end getAgentIdFromName

  @tailrec
  private[agents] def makeRequestWithRetries[T](request: Request[T], retries: Int = 3)
    (using context: ActorContext[Command]): Option[T] =
    if retries == 0 then
      None
    else
      val response = request.send()
      context.log.info("makeRequestWithRetries-response")
      if response.code.isSuccess then
        Some(response.body)
      else
        makeRequestWithRetries(request, retries - 1)
    end if
  end makeRequestWithRetries

  private[agents] def askLlm(prompt: String)(using context: ActorContext[Command]): Option[String] =
    val requestBody =
      Json.obj:
        "contents" -> Json.arr:
          Json.obj:
            "parts" -> Json.arr:
              Json.obj:
                "text" -> Json.fromString(prompt)

    val request = quickRequest
      .post(uri"$geminiBaseUrl")
      .header("Content-Type", "application/json")
      .header("X-goog-api-key", geminiApiKey)
      .body(requestBody.noSpaces)

    val maybeResponse = makeRequestWithRetries(request)
    context.log.info("askLlm-maybeResponse")
    maybeResponse.flatMap: response =>
      parse(response).toOption.flatMap:
        _.hcursor
          .downField("candidates").downArray
          .downField("content")
          .downField("parts").downArray
          .get[String]("text").toOption
  end askLlm

  private[agents] def standardizePrompt(
    instructions: Option[String] = None, input: Option[String] = None
  ): String =
    s"""
       |<Prompt>
       |  <Instructions>${instructions.getOrElse("").trim}</Instructions>
       |  <Input>${input.getOrElse("").trim}</Input>
       |</Prompt>
       |""".stripMargin
  end standardizePrompt

  private[agents] def getContentFromJsonField(json: String, field: String): String =
    val cleaned = json.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim
    parse(cleaned).toOption
      .flatMap(_.hcursor.get[String](field).toOption)
      .getOrElse("")
  end getContentFromJsonField

end Agent

object AgentsBehaviours:
  private given Decoder[Instructions] = Decoder.forProduct5(
    "start",
    "next",
    "review",
    "end",
    "call-tool"
  )(Instructions.apply)

  private[agents] sealed case class Instructions(
    start: String,
    next: String,
    review: String,
    end: String,
    callTools: String
  )

  private type Behaviour = Map[String, Instructions]

  private val behavioursPath: String = "src/main/resources/agents/behaviours.yml"

  private[agents] val behaviours: Behaviour = getBehaviours

  private def processJson(
    json: Either[ParsingFailure, Json]
  ): Either[Error, Behaviour] =
    json
      .leftMap(err => err: Error)
      .flatMap(_.as[Behaviour])
  end processJson

  private def readBehaviours: Option[List[Either[Error, Behaviour]]] =
    Try(new FileReader(behavioursPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match
      case Left(err) => None
      case Right(configs) =>
        Some(configs)
  end readBehaviours

  private def getBehaviours: Behaviour =
    readBehaviours match
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, Instructions])(_ ++ _)
      case None => Map.empty
  end getBehaviours

  private[agents] def getbehaviourByCommand(
    behaviours: Behaviour, agentLabel: String, command: Command
  ): Option[String] =
    for
      config <- behaviours.get(agentLabel)
      prompt <- command match
        case Command.Start(_) => Some(config.start)
        case Command.Next(_) => Some(config.next)
        case Command.Review(_) => Some(config.review)
        case Command.End(_) => Some(config.end)
        case Command.CallTool(_) => Some(config.callTools)
    yield prompt
  end getbehaviourByCommand

end AgentsBehaviours
