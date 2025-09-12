package alex.demo
package agents

import agents.Agent.*

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WorkerAgent extends Agent {
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeCvEndpoint = dotenv.get("EMPLOYEE_CV_ENDPOINT").split("/")
  private val employeeIdEndpoint = dotenv.get("EMPLOYEE_ID_ENDPOINT").split("/")
  private val employeeProfileEndpoint = dotenv.get("EMPLOYEE_PROFILE_ENDPOINT").split("/")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT").split("/")
  private val internalDocsEndpoint = dotenv.get("INTERNAL_DOCS_ENDPOINT").split("/")

  private object ActionType {
    val Retrieve = "retrieve"
    val Upload = "upload"
  }

  def doStart(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(prompt)
    askLlm(prompt) match {
      case Some(response) =>
        context.log.info(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        context.self ! Command.CallTool(props = CommandProps(
          input = List(ActionType.Retrieve, response), from = commandProps.from
        ))
        Behaviors.same
      case _ =>
        context.log.info(err("WorkerAgent doStart askLlm None"))
        Behaviors.stopped
    }
  }

  def doNext(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(prompt)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), selfInstructions :: _) =>
        context.log.info(response)
        val result = getContentFromJsonField(response, "result")
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        Files.write(
          Paths.get(s"target/doNext-${context.self.path.name}-$timestamp.html"),
          result.getBytes(StandardCharsets.UTF_8)
        )
        context.self ! Command.Review(props = CommandProps(
          input = List(selfInstructions, result), from = commandProps.from
        ))
        Behaviors.same
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"WorkerAgent doNext askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
        Behaviors.stopped
    }
  }

  def doReview(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(prompt)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), selfInstructions :: taskOutput :: _) =>
        context.log.info(response)
        val result = getContentFromJsonField(response, "result")
        val thoughts = getContentFromJsonField(response, "thoughts")
        // log thoughts
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        Files.write(
          Paths.get(s"target/doReview-${context.self.path.name}-$timestamp.html"),
          result.getBytes(StandardCharsets.UTF_8)
        )
        context.self ! Command.CallTool(props = CommandProps(
          input = List(ActionType.Upload, selfInstructions, result), from = commandProps.from
        ))
        Behaviors.same
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"WorkerAgent doReview askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
        Behaviors.stopped
    }
  }

  def doEnd(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info("WorkerAgent doEnd")
    (commandProps.input, commandProps.from) match {
      case (taskResult :: secret :: Nil, Some(supervisorRef)) =>
        supervisorRef ! Command.Next(props = CommandProps(input = List(secret), from = Some(context.self)))
        Behaviors.stopped
      case (maybeInput, maybeFrom) =>
        context.log.info(err(s"WorkerAgent doEnd commandProps.input <$maybeInput> commandProps.from <$maybeFrom>"))
        Behaviors.stopped
    }
  }

  def doCallTool(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(prompt)
    commandProps.input match {
      case ActionType.Retrieve :: selfInstructions :: Nil =>
        retrieveEmployeeData(selfInstructions, commandProps.from)
      case ActionType.Upload :: selfInstructions :: taskResult :: Nil =>
        uploadEmployeeData(selfInstructions, taskResult, commandProps.from)
      case maybeInput =>
        context.log.info(err(s"WorkerAgent doCallTool commandProps.input <$maybeInput>"))
        Behaviors.stopped
    }
  }

  private def retrieveEmployeeData(selfInstructions: String, supervisorRef: Option[ActorRef[Command]])
    (using context: ActorContext[Command]): Behavior[Command] = {
    val name = context.self.path.name
    val role = getContentFromJsonField(selfInstructions, "role")
    val secret = getContentFromJsonField(selfInstructions, "secret")
    role match {
      case Label.ProfileWorker =>
        callGetEmployeeCvTool(secret, name) match {
          case Some(employeeCv) =>
            context.self ! Command.Next(props = CommandProps(input = List(selfInstructions, employeeCv), from = supervisorRef))
            Behaviors.same
          case _ =>
            context.log.info(err("WorkerAgent retrieveEmployeeData employeeCv None"))
            Behaviors.stopped
        }
      case Label.DocsWorker =>
        (callGetEmployeeIdTool(secret, name), callGetInternalDocsTool(name)) match {
          case (Some(employeeId), internalDocs@_ :: _) =>
            for (internalDoc <- internalDocs) {
              context.self ! Command.Next(props = CommandProps(
                input = List(selfInstructions, employeeId, internalDoc), from = supervisorRef
              ))
            }
            Behaviors.same
          case (maybeEmployeeId, maybeInternalDocs) =>
            context.log.info(err(s"WorkerAgent retrieveEmployeeData employeeId <$maybeEmployeeId> internalDocs <$maybeInternalDocs>"))
            Behaviors.stopped
        }
      case _ =>
        context.log.info(err("WorkerAgent retrieveEmployeeData role None"))
        Behaviors.stopped
    }
  }

  private def uploadEmployeeData(selfInstructions: String, taskResult: String, supervisorRef: Option[ActorRef[Command]])
    (using context: ActorContext[Command]): Behavior[Command] = {
    val name = context.self.path.name
    val role = getContentFromJsonField(selfInstructions, "role")
    val secret = getContentFromJsonField(selfInstructions, "secret")
    role match {
      case Label.ProfileWorker =>
        callPostEmployeeProfileTool(secret, name, taskResult) match {
          case Some(_) =>
            context.self ! Command.End(props = CommandProps(input = List(taskResult, secret), from = supervisorRef))
            Behaviors.same
          case _ =>
            context.log.info(err("WorkerAgent uploadEmployeeData callPostEmployeeProfileTool None"))
            Behaviors.stopped
        }
      case Label.DocsWorker =>
        callPostEmployeeDocsTool(secret, name, taskResult) match {
          case Some(_) =>
            context.self ! Command.End(props = CommandProps(input = List(taskResult, secret), from = supervisorRef))
            Behaviors.same
          case _ =>
            context.log.info(err("WorkerAgent uploadEmployeeData callPostEmployeeDocsTool None"))
            Behaviors.stopped
        }
      case _ =>
        context.log.info(err("WorkerAgent uploadEmployeeData role None"))
        Behaviors.stopped
    }
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