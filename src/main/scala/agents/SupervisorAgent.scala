package alex.demo
package agents

import agents.Agent.*

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

import java.awt.Desktop
import java.io.File
import scala.collection.mutable

object SupervisorAgent extends Agent {
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeProfileEndpoint = dotenv.get("EMPLOYEE_PROFILE_ENDPOINT").split("/")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT").split("/")

  private val workerRefs: mutable.Set[String] = mutable.Set.empty

  def doStart(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${Label.SupervisorId} received a START command.")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.debug(prompt)
    askLlm(prompt) match {
      case Some(response) =>
        context.log.debug(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        context.log.info(s"${Label.SupervisorId}: $thoughts")
        val profileWorker = context.spawn(WorkerAgent(), s"${Label.Worker}-${Label.ProfileWorkerId}")
        val profileWorkerInstructions = getContentFromJsonField(response, Label.ProfileWorker)
        profileWorker ! Command.Start(
          props = CommandProps(input = List(profileWorkerInstructions), from = Some(context.self))
        )
        workerRefs.add(profileWorker.path.name)
        val docsWorker = context.spawn(WorkerAgent(), s"${Label.Worker}-${Label.DocsWorkerId}")
        val docsWorkerInstructions = getContentFromJsonField(response, Label.DocsWorker)
        docsWorker ! Command.Start(
          props = CommandProps(input = List(docsWorkerInstructions), from = Some(context.self))
        )
        workerRefs.add(docsWorker.path.name)
        context.log.info(s"${Label.SupervisorId} delegated tasks to ${workerRefs.toList.map(w => getAgentIdFromName(w)).mkString(", ")}")
      case _ =>
        context.log.info(err(s"Gemini LLM failed to respond to ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err("SupervisorAgent doStart askLlm None"))
    }
    Behaviors.same
  }

  def doNext(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${Label.SupervisorId} received a NEXT command.")
    (commandProps.input, commandProps.from) match {
      case (secret :: Nil, Some(workerRef)) =>
        context.log.info(s"${Label.SupervisorId} received a taskwork completion confirmation from ${getAgentIdFromName(workerRef.path.name)}.")
        context.stop(workerRef)
        workerRefs.remove(workerRef.path.name)
        if (workerRefs.isEmpty) context.self ! Command.Review(props = CommandProps(input = List(secret)))
      case _ =>
        context.log.info(err(s"${getAgentIdFromName(context.self.path.name)} received unknown confirmation"))
        context.log.debug(err("SupervisorAgent doNext commandProps.from None"))
    }
    Behaviors.same
  }

  def doReview(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${Label.SupervisorId} received a REVIEW command.")
    commandProps.input match {
      case secret :: Nil =>
        val cwd = new File(".").getCanonicalPath
        val profilePath = s"$cwd/server/DB/EMPLOYEES/PROFILES/profile_$secret.html"
        val profileFile = new File(profilePath)
        val docsPath = s"$cwd/server/DB/EMPLOYEES/DOCS/docs_$secret"
        val docsDirectory = new File(docsPath)
        if (profileFile.exists() && docsDirectory.exists()) {
          val profileUri = profileFile.toURI
          val docsUri = docsDirectory.toURI
          if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop.browse(profileUri)
            docsDirectory.listFiles().foreach(doc => Desktop.getDesktop.browse(doc.toURI))
          } else {
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)} can't open the documents in your browser"))
            context.log.debug(err("SupervisorAgent doReview Desktop.Action.Browse not supported"))
          }
        }
        print("Check the documents. Does everything look good: ")
        val userFeedback = takeInput().getOrElse("yes")
        context.self ! Command.End(props = CommandProps(input = List(secret, userFeedback)))
      case _ =>
        context.log.info(err(s"${getAgentIdFromName(context.self.path.name)} got nothing to review"))
        context.log.debug(err("SupervisorAgent doReview commandProps.input None"))
    }
    Behaviors.same
  }

  def doEnd(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${Label.SupervisorId} received an END command.")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), secret :: userFeedback :: Nil) =>
        val isUserPleased = getContentFromJsonField(response, "isUserPleased")
        val llmFeedback = getContentFromJsonField(response, "llmFeedback")
        val thoughts = getContentFromJsonField(response, "thoughts")
        context.log.info(s"${Label.SupervisorId}: $thoughts")
        if (isUserPleased == "false") {
          if (callDeleteEmployeeDocsTool(secret, context.self.path.name).isEmpty) {
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s call to DeleteEmployeeDocs() tool failed"))
            context.log.debug(err("SupervisorAgent doEnd callDeleteEmployeeDocsTool None"))
          }
          if (callDeleteEmployeeProfileTool(secret, context.self.path.name).isEmpty) {
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s call to DeleteEmployeeProfile() tool failed"))
            context.log.debug(err("SupervisorAgent doEnd callDeleteEmployeeProfileTool None"))
          }
          context.self ! Command.Start(props = CommandProps(instructions = List(llmFeedback), input = List(secret)))
        } else {
          println(llmFeedback)
        }
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"either Gemini LLM failed to respond or the required input is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err(s"SupervisorAgent doEnd askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
    }
    Behaviors.same
  }

  def doCallTool(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    Behaviors.same
  }

  private def callDeleteEmployeeDocsTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the DeleteEmployeeDocs('$secret') tool.")
    val request = basicRequest
      .delete(uri"$serverBaseUrl:$serverPort/${employeeDocsEndpoint(0)}/${employeeDocsEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callDeleteEmployeeProfileTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the DeleteEmployeeProfile('$secret') tool.")
    val request = basicRequest
      .delete(uri"$serverBaseUrl:$serverPort/${employeeProfileEndpoint(0)}/${employeeProfileEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }
}