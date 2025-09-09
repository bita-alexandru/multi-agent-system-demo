package alex.demo
package agents

import agents.Agent.{Command, CommandProps, askLlm, makeRequestWithRetries}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object OrchestratorAgent extends Agent:
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    systemPrompt.foreach(context.log.info)
    val worker = context.spawn(WorkerAgent(), "worker")
    worker ! Command.CallTool()
    Behaviors.same
  end doStart

  def doNext(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doNext

  def doReview(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doReview

  def doEnd(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doEnd

  def doCallTool(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doCallTool

  private def callDeleteEmployeeDocsTool(secret: String, filename: String, document: String)
    (using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .delete(uri"$serverBaseUrl:$serverPort/$employeeDocsEndpoint?secret=$secret")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  end callDeleteEmployeeDocsTool

end OrchestratorAgent

