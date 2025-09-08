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

  enum Command(val commandProps: CommandProps = CommandProps()):
    case Start(props: CommandProps = CommandProps()) extends Command(props)
    case Next(props: CommandProps = CommandProps()) extends Command(props)
    case Review(props: CommandProps = CommandProps()) extends Command(props)
    case End(props: CommandProps = CommandProps()) extends Command(props)
    case CallTool(props: CommandProps = CommandProps()) extends Command(props)

  sealed case class CommandProps(
    prompt: Option[String] = None,
    from: Option[ActorRef[Agent]] = None,
    contacts: Option[List[ActorRef[Agent]]] = None
  )

  private def getSystemPrompts(command: Command)(using context: ActorContext[Command]): Option[String] =
    context.log.info("getSystemPrompts")
    AgentsBehaviours.getAgentBehaviourByCommand(
      agentsBehaviours = AgentsBehaviours.agentsBehaviours,
      agentId = context.system.name,
      command = command
    )
  end getSystemPrompts

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
    val response = maybeResponse.flatMap: response =>
      parse(response).toOption.flatMap:
        _.hcursor
          .downField("candidates").downArray
          .downField("content")
          .downField("parts").downArray
          .get[String]("text").toOption
    response
  end askLlm

  private[agents] def promptToTemplate(
    wholePrompt: Option[String] = None, systemPrompt: Option[String] = None, userInput: Option[String] = None
  ): String =
    (wholePrompt, systemPrompt, userInput) match
      case (Some(prompt), _, _) => s"<Prompt>${prompt.trim}</Prompt>"
      case (_, Some(prompt), _) => s"<SystemInstructions>${prompt.trim}</SystemInstructions>"
      case (_, _, Some(prompt)) => s"<UserInput>${prompt.trim}</UserInput>"
      case _ => ""
  end promptToTemplate

end Agent

object AgentsBehaviours:
  private given Decoder[AgentInstructions] = Decoder.forProduct5(
    "start",
    "next",
    "review",
    "end",
    "call-tool"
  )(AgentInstructions.apply)

  private[agents] sealed case class AgentInstructions(
    start: String,
    next: String,
    review: String,
    end: String,
    callTools: String
  )

  private type AgentBehaviour = Map[String, AgentInstructions]

  private val agentsBehavioursPath: String = "src/main/resources/agents/behaviours.yml"

  private[agents] val agentsBehaviours: AgentBehaviour = getAgentsBehaviours

  private def processJson(
    json: Either[ParsingFailure, Json]
  ): Either[Error, AgentBehaviour] =
    json
      .leftMap(err => err: Error)
      .flatMap(_.as[AgentBehaviour])
  end processJson

  private def readAgentsBehaviours: Option[List[Either[Error, AgentBehaviour]]] =
    Try(new FileReader(agentsBehavioursPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match
      case Left(err) => None
      case Right(configs) =>
        Some(configs)
  end readAgentsBehaviours

  private def getAgentsBehaviours: AgentBehaviour =
    readAgentsBehaviours match
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, AgentInstructions])(_ ++ _)
      case None => Map.empty
  end getAgentsBehaviours

  private[agents] def getAgentBehaviourByCommand(
    agentsBehaviours: AgentBehaviour, agentId: String, command: Command
  ): Option[String] =
    for
      config <- agentsBehaviours.get(agentId)
      prompt <- command match
        case Command.Start(_) => Some(config.start)
        case Command.Next(_) => Some(config.next)
        case Command.Review(_) => Some(config.review)
        case Command.End(_) => Some(config.end)
        case Command.CallTool(_) => Some(config.callTools)
    yield prompt
  end getAgentBehaviourByCommand

end AgentsBehaviours
