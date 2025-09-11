package alex.demo
package agents

import agents.Agent.{Command, CommandProps, Label, askLlm, getContentFromJsonField, makeRequestWithRetries, standardizePrompt}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object SupervisorWorker extends Agent {
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = standardizePrompt(systemPrompt, commandProps.input)
    context.log.info(prompt)
    //    val response = """{"profileWorker": "abc", "docsWorker": "123"}"""
    askLlm(prompt) match {
      case None =>
        context.log.info("supervisorAgent doStart askLlm FAILED")
        Behaviors.same
      case Some(response) =>
        context.log.info(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        val profileWorker = context.spawn(WorkerAgent(), s"${Label.Worker.value}-peter")
        val profileWorkerInstructions = getContentFromJsonField(response, Label.ProfileWorker.value)
        profileWorker ! Command.Start(
          props = CommandProps(input = Some(profileWorkerInstructions), from = Some(context.self))
        )
        val docsWorker = context.spawn(WorkerAgent(), s"${Label.Worker.value}-daisy")
        val docsWorkerInstructions = getContentFromJsonField(response, Label.DocsWorker.value)
        docsWorker ! Command.Start(
          props = CommandProps(input = Some(docsWorkerInstructions), from = Some(context.self))
        )
        Behaviors.same
    }
  }

  def doNext(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same

  def doReview(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same

  def doEnd(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same

  def doCallTool(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same

  private def callDeleteEmployeeDocsTool(secret: String, caller: String, document: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .delete(uri"$serverBaseUrl:$serverPort/$employeeDocsEndpoint?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  }
}