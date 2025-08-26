package alex.demo
package agents

import agents.Agent.{Command, CommandProps, getSystemPrompts}
import utils.AgentConfigs

import io.circe.Json
import io.circe.parser.*
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import sttp.client4.quick.*

import scala.annotation.tailrec

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

  private[agents] def ask(prompt: String): Option[String] =
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
  end ask
end Agent
