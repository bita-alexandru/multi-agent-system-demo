package alex.demo
package agents

import agents.Agent.{Command, CommandProps, Label, askLlm, err, getContentFromJsonField, makeRequestWithRetries, standardizePrompt}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object WorkerAgent extends Agent {
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeCvEndpoint = dotenv.get("EMPLOYEE_CV_ENDPOINT").split("/")
  private val employeeIdEndpoint = dotenv.get("EMPLOYEE_ID_ENDPOINT").split("/")
  private val employeeProfileEndpoint = dotenv.get("EMPLOYEE_PROFILE_ENDPOINT").split("/")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT").split("/")
  private val internalDocsEndpoint = dotenv.get("INTERNAL_DOCS_ENDPOINT").split("/")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = standardizePrompt(systemPrompt, commandProps.input)
    context.log.info(prompt)
    askLlm(prompt) match {
      case None =>
        context.log.info(err("workerAgent doStart askLlm None"))
        Behaviors.same
      case Some(response) =>
        context.log.info(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        context.self ! Command.CallTool(props = CommandProps(input = Some(response), from = commandProps.from))
        Behaviors.same
    }
  }

  def doNext(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = standardizePrompt(systemPrompt, commandProps.input)
    context.log.info(prompt)
    Behaviors.same
  }

  def doReview(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    systemPrompt.foreach(context.log.info)
    Behaviors.same
  }

  def doEnd(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(systemPrompt.getOrElse("WorkerAgent doEnd"))
    Behaviors.same
  }

  def doCallTool(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = standardizePrompt(systemPrompt, commandProps.input)
    context.log.info(prompt)
    val input = commandProps.input.getOrElse("")
    val role = getContentFromJsonField(input, "role")
    val secret = getContentFromJsonField(input, "secret")
    val name = context.self.path.name
    role match {
      case Label.ProfileWorker.value =>
        callGetEmployeeCvTool(secret, name) match {
          case Some(employeeCv) =>
            context.self ! Command.Next(props = CommandProps(input = Some(employeeCv), from = commandProps.from))
          case _ =>
            context.log.info(err("WorkerAgent doCallTool employeeCv None"))
        }
      case Label.DocsWorker.value =>
        (callGetEmployeeIdTool(secret, name), callGetInternalDocsTool(name)) match {
          case (Some(employeeId), internalDocs@_ :: _) =>
            // iterate over internal docs to send msg
            context.self ! Command.Next(props = CommandProps(input = Some(employeeId), from = commandProps.from))
          case _ =>
            context.log.info(err("WorkerAgent doCallTool (employeeId, internalDocs) None"))
        }
      case _ =>
        context.log.info(err("WorkerAgent doCallTool commandProps.input None"))
    }
    Behaviors.same
  }

  private def callGetEmployeeCvTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/${employeeCvEndpoint(0)}/${employeeCvEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callGetEmployeeIdTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/${employeeIdEndpoint(0)}/${employeeIdEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callPostEmployeeProfileTool(secret: String, caller: String, profile: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/${employeeProfileEndpoint(0)}/${employeeProfileEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(profile.trim)
    makeRequestWithRetries(request)
  }

  private def callPostEmployeeDocsTool(secret: String, caller: String, document: String)
    (using context: ActorContext[Command]): Option[String] = {
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/${employeeDocsEndpoint(0)}/${employeeDocsEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  }

  private def callGetInternalDocsTool(caller: String)(using context: ActorContext[Command]): List[String] = {
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/${internalDocsEndpoint(0)}/${internalDocsEndpoint(1)}?caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request).map { docs =>
        docs
          .split("(?i)(?=<!DOCTYPE html>)")
          .toList
          .filter(_.nonEmpty)
      }
      .getOrElse(Nil)
  }

}