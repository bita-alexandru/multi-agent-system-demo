package alex.demo
package agents

import agents.Agent.{Command, CommandProps, askLlm, getContentFromJsonField, makeRequestWithRetries, standardizePrompt}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object SupervisorWorker extends Agent:
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    val prompt = standardizePrompt(systemPrompt, commandProps.input)
    context.log.info(prompt)
    //    val response = """{"profileWorker": "abc", "docsWorker": "123"}"""
    askLlm(prompt) match
      case None =>
        context.log.info("smth bad happened rip")
        Behaviors.same
      case Some(response) =>
        context.log.info(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        val profileWorker = context.spawn(WorkerAgent(), "worker-agent-peter")
        val profileWorkerInstructions = getContentFromJsonField(response, "profileWorker")
        profileWorker ! Command.Start(
          props = CommandProps(prompt = Some(profileWorkerInstructions), input = commandProps.input, from = Some(context.self))
        )
        val docsWorker = context.spawn(WorkerAgent(), "worker-agent-daisy")
        val docsWorkerInstructions = getContentFromJsonField(response, "docsWorker")
        docsWorker ! Command.Start(
          props = CommandProps(prompt = Some(docsWorkerInstructions), input = commandProps.input, from = Some(context.self))
        )
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

  private def callDeleteEmployeeDocsTool(secret: String, caller: String, document: String)
    (using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .delete(uri"$serverBaseUrl:$serverPort/$employeeDocsEndpoint?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  end callDeleteEmployeeDocsTool

end SupervisorWorker

