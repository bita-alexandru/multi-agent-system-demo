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
    context.log.info("SupervisorAgent doStart")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(prompt)
    askLlm(prompt) match {
      case Some(response) =>
        context.log.info(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        val profileWorker = context.spawn(WorkerAgent(), s"${Label.Worker}-peter")
        val profileWorkerInstructions = getContentFromJsonField(response, Label.ProfileWorker)
        profileWorker ! Command.Start(
          props = CommandProps(input = List(profileWorkerInstructions), from = Some(context.self))
        )
        workerRefs.add(profileWorker.path.name)
        val docsWorker = context.spawn(WorkerAgent(), s"${Label.Worker}-daisy")
        val docsWorkerInstructions = getContentFromJsonField(response, Label.DocsWorker)
        docsWorker ! Command.Start(
          props = CommandProps(input = List(docsWorkerInstructions), from = Some(context.self))
        )
        workerRefs.add(docsWorker.path.name)
        Behaviors.same
      case _ =>
        context.log.info(err("SupervisorAgent doStart askLlm None"))
        Behaviors.stopped
    }
  }

  def doNext(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info("SupervisorAgent doNext")
    (commandProps.input, commandProps.from) match {
      case (secret :: Nil, Some(workerRef)) =>
        workerRefs.remove(workerRef.path.name)
        if (workerRefs.isEmpty) context.self ! Command.Review(props = CommandProps(input = List(secret)))
        Behaviors.same
      case _ =>
        context.log.info(err("SupervisorAgent doNext commandProps.from None"))
        Behaviors.stopped
    }
  }

  def doReview(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info("SupervisorAgent doReview")
    commandProps.input match {
      case secret :: Nil =>
        val cwd = new File(".").getCanonicalPath
        val profilePath = s"$cwd/server/DB/EMPLOYEES/PROFILES/profile_$secret"
        val profileFile = new File(profilePath)
        val docsPath = s"$cwd/server/DB/EMPLOYEES/DOCS/docs_$secret"
        val docsDirectory = new File(docsPath)
        if (profileFile.exists() && docsDirectory.exists()) {
          val profileUri = profileFile.toURI
          val docsUri = docsDirectory.toURI
          if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop.browse(profileUri) // opens in the system default browser
            Desktop.getDesktop.browse(docsUri) // opens in the system default browser
          } else {
            context.log.info(err("SupervisorAgent doReview Desktop.Action.Browse not supported"))
          }
        }
        println("Does everything look good?")
        val userFeedback = takeInput().getOrElse("yes")
//        val userFeedback = "no, i want my signature to be just my name initials"
        context.self ! Command.End(props = CommandProps(input = List(secret, userFeedback)))
        Behaviors.same
      case _ =>
        context.log.info(err("SupervisorAgent doReview commandProps.input None"))
        Behaviors.stopped
    }
  }

  def doEnd(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info("SupervisorAgent doEnd")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), secret :: userFeedback :: Nil) =>
        val isUserPleased = getContentFromJsonField(response, "isUserPleased")
        val llmFeedback = getContentFromJsonField(response, "llmFeedback")
        val thoughts = getContentFromJsonField(response, "thoughts")
        if (isUserPleased == "false") {
          if (callDeleteEmployeeDocsTool(secret, context.self.path.name).isEmpty)
            context.log.info(err("SupervisorAgent doEnd callDeleteEmployeeDocsTool None"))
          if (callDeleteEmployeeProfileTool(secret, context.self.path.name).isEmpty)
            context.log.info(err("SupervisorAgent doEnd callDeleteEmployeeProfileTool None"))
          context.self ! Command.Start(props = CommandProps(instructions = List(llmFeedback), input = List(secret)))
          Behaviors.same
        } else {
          println(llmFeedback)
          Behaviors.stopped
        }
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"SupervisorAgent doEnd askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
        Behaviors.stopped
    }
  }

  def doCallTool(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same

  private def callDeleteEmployeeDocsTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .delete(uri"$serverBaseUrl:$serverPort/${employeeDocsEndpoint(0)}/${employeeDocsEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callDeleteEmployeeProfileTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .delete(uri"$serverBaseUrl:$serverPort/${employeeProfileEndpoint(0)}/${employeeProfileEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }
}