package alex.demo
package agents

import agents.Agent.{Command, CommandProps, colorizeActionLog, getAgentIdFromName, getSystemInstructions}

import io.circe.*
import io.circe.parser.*
import cats.syntax.either.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop}
import sttp.client4.*

import java.io.FileReader
import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

trait Agent {
  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorContext[Command] = context

      Behaviors.receiveMessage[Command] {
        case Command.Start(props) =>
          doStart(getSystemInstructions(Command.Start(props)), props)
        case Command.Next(props) =>
          doNext(getSystemInstructions(Command.Next(props)), props)
        case Command.Review(props) =>
          doReview(getSystemInstructions(Command.Review(props)), props)
        case Command.End(props) =>
          doEnd(getSystemInstructions(Command.End(props)), props)
        case Command.CallTool(props) =>
          doCallTool(getSystemInstructions(Command.CallTool(props)), props)
        case null => Behaviors.same
      }.receiveSignal {
        case (context, PostStop) =>
          context.log.info(colorizeActionLog(s"${getAgentIdFromName(context.self.path.name)} stopped."))
          Behaviors.same
        case idk =>
          context.log.info(colorizeActionLog(s"${context.self.path.name} received <$idk>"))
          Behaviors.same
      }
    }

  def doStart(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doNext(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doReview(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doEnd(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]

  def doCallTool(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command]
}

object Agent {
  private val geminiApiKey: String = dotenv.get("GEMINI_API_KEY")
  private val geminiBaseUrl: String = dotenv.get("GEMINI_BASE_URL")

  private[agents] val err: (trace: String) => String = trace => s"Something went wrong: $trace"

  object Label {
    val User = "user"
    val Supervisor = "supervisor-agent"
    val SupervisorId = "Sam-Supervisor"
    val Worker = "worker-agent"
    val Unknown = "unknown"
    val ProfileWorker = "profileWorker"
    val ProfileWorkerId = "Peter-Profile"
    val DocsWorker = "docsWorker"
    val DocsWorkerId = "Daisy-Docs"
  }

  enum Command(val commandProps: CommandProps = CommandProps()) {
    case Start(props: CommandProps = CommandProps()) extends Command(props)
    case Next(props: CommandProps = CommandProps()) extends Command(props)
    case Review(props: CommandProps = CommandProps()) extends Command(props)
    case End(props: CommandProps = CommandProps()) extends Command(props)
    case CallTool(props: CommandProps = CommandProps()) extends Command(props)
  }

  sealed case class CommandProps(
    instructions: List[String] = Nil,
    input: List[String] = Nil,
    from: Option[ActorRef[Command]] = None
  )

  private def getSystemInstructions(command: Command)(using context: ActorContext[Command]): Option[String] =
    AgentsBehaviours.getbehaviourByCommand(
      behaviours = AgentsBehaviours.behaviours,
      agentLabel = getAgentLabelFromName(context.self.path.name),
      command = command
    )

  private def getAgentLabelFromName(name: String): String =
    name match {
      case Label.User => Label.Supervisor
      // @formatter:off
      case s if s.startsWith(s"${Label.Worker}-") => Label.Worker
      // @formatter:on
      case _ => Label.Worker
    }

  private[agents] def getAgentIdFromName(name: String): String =
    name match {
      case Label.User => Label.SupervisorId
      // @formatter:off
      case s if s.startsWith(s"${Label.Worker}-") => s.stripPrefix(s"${Label.Worker}-")
      // @formatter:on
      case _ => Label.Unknown
    }

  private def colorizeAgentId(id: String, resetColor: String = "\u001b[0;0m"): String =
    id match {
      case Label.SupervisorId => s"\u001b[1;36m$id$resetColor"
      case Label.DocsWorkerId => s"\u001b[1;35m$id$resetColor"
      case Label.ProfileWorkerId => s"\u001b[1;33m$id$resetColor"
      case _ => s"\u001b[37m$id\u001b$resetColor"
    }

  private[agents] def colorizeActionLog(log: String): String = {
    List(Label.SupervisorId, Label.DocsWorkerId, Label.ProfileWorkerId)
      .foldLeft(s"\u001b[32m$log\u001b[0m")((acc, agentId) => acc.replace(agentId, colorizeAgentId(agentId, "\u001b[0;32m")))
  }

  private[agents] def colorizeErrorLog(log: String): String = {
    List(Label.SupervisorId, Label.DocsWorkerId, Label.ProfileWorkerId)
      .foldLeft(s"\u001b[31m$log\u001b[0m")((acc, agentId) => acc.replace(agentId, colorizeAgentId(agentId, "\u001b[0;31m")))
  }

  private[agents] def colorizeAgentThoughts(id: String, thoughts: String): String = {
    val bgColor = id match {
      case Label.SupervisorId => "\u001b[46m"
      case Label.DocsWorkerId => "\u001b[45m"
      case Label.ProfileWorkerId => "\u001b[43m"
      case _ => "\u001b[0;0m"
    }
    s"${colorizeAgentId(id)}: \u001b[30m$bgColor$thoughts\u001b[0;0m"
  }

  @tailrec
  private[agents] def makeRequestWithRetries[T](request: Request[Either[T, T]], retries: Int = 3, sleepDuration: Int = 1000)
    (using context: ActorContext[Command]): Option[T] = {
    if (retries == 0) None
    else {
      val backend = DefaultSyncBackend(options = BackendOptions.connectionTimeout(5.minutes))
      val response = request.readTimeout(Duration.Inf).send(backend)
      if (response.code.isSuccess) response.body.toOption
      else {
        Thread.sleep(sleepDuration)
        makeRequestWithRetries(request, retries - 1, sleepDuration * 2)
      }
    }
  }

  private[agents] def askLlm(prompt: String)(using context: ActorContext[Command]): Option[String] = {
    context.log.info(colorizeActionLog(s"${getAgentIdFromName(context.self.path.name)} is prompting the LLM."))
    val requestBody =
      Json.obj {
        "contents" -> Json.arr {
          Json.obj {
            "parts" -> Json.arr {
              Json.obj {
                "text" -> Json.fromString(prompt)
              }
            }
          }
        }
      }

    val request = basicRequest
      .post(uri"$geminiBaseUrl")
      .header("Content-Type", "application/json")
      .header("X-goog-api-key", geminiApiKey)
      .body(requestBody.noSpaces)

        val maybeResponse = makeRequestWithRetries(request)
    context.log.debug(s"askLlm maybeResponse <$maybeResponse>")
    maybeResponse.flatMap { response =>
      parse(response).toOption.flatMap {
        _.hcursor
          .downField("candidates").downArray
          .downField("content")
          .downField("parts").downArray
          .get[String]("text").map(sanitizeJsonContent).toOption
      }
    }
  }

  private def sanitizeJsonContent(json: String): String =
    json.replaceAll("(?s)```(?:json)?", "").trim

  private[agents] def makePrompt(
    instructionsList: List[String] = Nil, inputList: List[String] = Nil
  ): String = {
    val instructions = instructionsList.map { s =>
      s"    <Instructions>\n      ${s.trim}\n    </Instructions>"
    }.mkString("\n")
    val input = inputList.map { s =>
      s"    <Input>\n      ${s.trim}\n    </Input>"
    }.mkString("\n")
    s"""
       |<Prompt>
       |  <InstructionsList>
       |$instructions
       |  </InstructionsList>
       |  <InputList>
       |$input
       |  </InputList>
       |</Prompt>
       |""".stripMargin
  }

  private[agents] def getContentFromJsonField(json: String, field: String): String = {
    parse(sanitizeJsonContent(json)).toOption
      .flatMap(_.hcursor.get[String](field).toOption)
      .getOrElse("")
  }

}

object AgentsBehaviours {

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

  private def readBehaviours: Option[List[Either[Error, Behaviour]]] =
    Try(new FileReader(behavioursPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match {
      case Left(err) => None
      case Right(configs) =>
        Some(configs)
    }

  private def getBehaviours: Behaviour =
    readBehaviours match {
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, Instructions])(_ ++ _)
      case None => Map.empty
    }

  private[agents] def getbehaviourByCommand(
    behaviours: Behaviour, agentLabel: String, command: Command
  ): Option[String] =
    for {
      config <- behaviours.get(agentLabel)
      prompt <- command match {
        case Command.Start(_) => Some(config.start)
        case Command.Next(_) => Some(config.next)
        case Command.Review(_) => Some(config.review)
        case Command.End(_) => Some(config.end)
        case Command.CallTool(_) => Some(config.callTools)
      }
    } yield prompt

}
