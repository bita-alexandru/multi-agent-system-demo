package alex.demo
package agents

import agents.Agent.*

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

//import java.nio.charset.StandardCharsets
//import java.nio.file.{Files, Paths}
//import java.time.LocalDateTime
//import java.time.format.DateTimeFormatter

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
    context.log.info(s"${getAgentIdFromName(context.self.path.name)} received a START command.")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.debug(prompt)
    askLlm(prompt) match {
      case Some(response) =>
        context.log.debug(response)
        val thoughts = getContentFromJsonField(response, "thoughts")
        context.log.info(s"${getAgentIdFromName(context.self.path.name)}: $thoughts")
        context.self ! Command.CallTool(props = CommandProps(
          input = List(ActionType.Retrieve, response), from = commandProps.from
        ))
      case _ =>
        context.log.info(err(s"Gemini LLM failed to respond to ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err("WorkerAgent doStart askLlm None"))
    }
    Behaviors.same
  }

  def doNext(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${getAgentIdFromName(context.self.path.name)} received a NEXT command.")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.debug(prompt)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), selfInstructions :: additionalInput) =>
        context.log.debug(response)
        val result = getContentFromJsonField(response, "result")
        val thoughts = getContentFromJsonField(response, "thoughts")
        context.log.info(s"${getAgentIdFromName(context.self.path.name)}: $thoughts")
        //        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        //        Files.write(
        //          Paths.get(s"target/doNext-${context.self.path.name}-$timestamp.html"),
        //          result.getBytes(StandardCharsets.UTF_8)
        //        )
        additionalInput match {
          case List(employeeId, internalDoc, docIndex) =>
            context.self ! Command.Review(props = CommandProps(
              input = List(selfInstructions, result, docIndex), from = commandProps.from
            ))
          case _ =>
            context.self ! Command.Review(props = CommandProps(
              input = List(selfInstructions, result), from = commandProps.from
            ))
        }
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"either Gemini LLM failed to respond or the required input is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err(s"WorkerAgent doNext askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
    }
    Behaviors.same
  }

  def doReview(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${getAgentIdFromName(context.self.path.name)} received a REVIEW command.")
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.debug(prompt)
    (askLlm(prompt), commandProps.input) match {
      case (Some(response), selfInstructions :: taskOutput :: additionalInput) =>
        context.log.debug(response)
        val result = getContentFromJsonField(response, "result")
        val thoughts = getContentFromJsonField(response, "thoughts")
        context.log.info(s"${getAgentIdFromName(context.self.path.name)}: $thoughts")
        //        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        //        Files.write(
        //          Paths.get(s"target/doReview-${context.self.path.name}-$timestamp.html"),
        //          result.getBytes(StandardCharsets.UTF_8)
        //        )
        additionalInput match {
          case List(docIndex) =>
            context.self ! Command.CallTool(props = CommandProps(
              input = List(ActionType.Upload, selfInstructions, result, docIndex), from = commandProps.from
            ))
          case _ =>
            context.self ! Command.CallTool(props = CommandProps(
              input = List(ActionType.Upload, selfInstructions, result), from = commandProps.from
            ))
        }
      case (maybeResponse, maybeInput) =>
        context.log.info(err(s"either Gemini LLM failed to respond or the required input is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err(s"WorkerAgent doReview askLlm <$maybeResponse> commandProps.input <$maybeInput>"))
    }
    Behaviors.same
  }

  def doEnd(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    context.log.info(s"${getAgentIdFromName(context.self.path.name)} received an END command.")
    (commandProps.input, commandProps.from) match {
      case (List(secret), Some(supervisorRef)) =>
        supervisorRef ! Command.Next(props = CommandProps(input = List(secret), from = Some(context.self)))
      case (List(secret, docIndex), Some(supervisorRef)) =>
        if (docIndex == "0") {
          supervisorRef ! Command.Next(props = CommandProps(input = List(secret), from = Some(context.self)))
        }
      case (maybeInput, maybeFrom) =>
        context.log.info(err(s"either the required input or the supervisor's reference is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err(s"WorkerAgent doEnd commandProps.input <$maybeInput> commandProps.from <$maybeFrom>"))
    }
    Behaviors.same
  }

  def doCallTool(systemInstructions: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] = {
    val prompt = makePrompt(systemInstructions.toList.concat(commandProps.instructions), commandProps.input)
    context.log.info(s"${getAgentIdFromName(context.self.path.name)} received a CALL-TOOL command.")
    context.log.debug(prompt)
    commandProps.input match {
      case ActionType.Retrieve :: selfInstructions :: Nil =>
        retrieveEmployeeData(selfInstructions, commandProps.from)
      case ActionType.Upload :: selfInstructions :: taskResult :: additionalInput =>
        uploadEmployeeData(selfInstructions, taskResult, additionalInput, commandProps.from)
      case maybeInput =>
        context.log.info(err(s"required input is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err(s"WorkerAgent doCallTool commandProps.input <$maybeInput>"))
    }
    Behaviors.same
  }

  private def retrieveEmployeeData(selfInstructions: String, supervisorRef: Option[ActorRef[Command]])
    (using context: ActorContext[Command]): Unit = {
    val name = context.self.path.name
    val role = getContentFromJsonField(selfInstructions, "role")
    val secret = getContentFromJsonField(selfInstructions, "secret")
    role match {
      case Label.ProfileWorker =>
        callGetEmployeeCvTool(secret, name) match {
          case Some(employeeCv) =>
            context.self ! Command.Next(props = CommandProps(input = List(selfInstructions, employeeCv), from = supervisorRef))
          case _ =>
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s call to GetEmployeeCvTool() tool failed"))
            context.log.debug(err("WorkerAgent retrieveEmployeeData employeeCv None"))
        }
      case Label.DocsWorker =>
        (callGetEmployeeIdTool(secret, name), callGetInternalDocsTool(name)) match {
          case (Some(employeeId), internalDocs@_ :: _) =>
            for ((internalDoc, docIndex) <- internalDocs.zipWithIndex.reverse) {
              context.self ! Command.Next(props = CommandProps(
                input = List(selfInstructions, employeeId, internalDoc, docIndex.toString), from = supervisorRef
              ))
            }
          case (maybeEmployeeId, maybeInternalDocs) =>
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s either call to GetEmployeeIdTool() or GetInternalDocs() tool failed"))
            context.log.debug(err(s"WorkerAgent retrieveEmployeeData employeeId <$maybeEmployeeId> internalDocs <$maybeInternalDocs>"))
        }
      case _ =>
        context.log.info(err(s"required role is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err("WorkerAgent retrieveEmployeeData role None"))
    }
  }

  private def uploadEmployeeData(selfInstructions: String, taskResult: String, additionalInput: List[String], supervisorRef: Option[ActorRef[Command]])
    (using context: ActorContext[Command]): Unit = {
    val name = context.self.path.name
    val role = getContentFromJsonField(selfInstructions, "role")
    val secret = getContentFromJsonField(selfInstructions, "secret")
    role match {
      case Label.ProfileWorker =>
        callPostEmployeeProfileTool(secret, name, taskResult) match {
          case Some(_) =>
            context.self ! Command.End(props = CommandProps(input = List(secret), from = supervisorRef))
          case _ =>
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s call to PostEmployeeProfile() tool failed"))
            context.log.debug(err("WorkerAgent uploadEmployeeData callPostEmployeeProfileTool None"))
        }
      case Label.DocsWorker =>
        (callPostEmployeeDocsTool(secret, name, taskResult), additionalInput) match {
          case (Some(_), List(docIndex)) =>
            context.self ! Command.End(props = CommandProps(input = List(secret, docIndex), from = supervisorRef))
          case _ =>
            context.log.info(err(s"${getAgentIdFromName(context.self.path.name)}'s call to PostEmployeeDocs() tool failed"))
            context.log.debug(err("WorkerAgent uploadEmployeeData callPostEmployeeDocsTool None"))
        }
      case _ =>
        context.log.info(err(s"required role is missing for ${getAgentIdFromName(context.self.path.name)}"))
        context.log.debug(err("WorkerAgent uploadEmployeeData role None"))
    }
  }

  private def callGetEmployeeCvTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the GetEmployeeCvTool('$secret') tool.")
    val request = basicRequest
      .get(uri"$serverBaseUrl:$serverPort/${employeeCvEndpoint(0)}/${employeeCvEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callGetEmployeeIdTool(secret: String, caller: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the GetEmployeeIdTool('$secret') tool.")
    val request = basicRequest
      .get(uri"$serverBaseUrl:$serverPort/${employeeIdEndpoint(0)}/${employeeIdEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  }

  private def callPostEmployeeProfileTool(secret: String, caller: String, profile: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the PostEmployeeProfile('$secret') tool.")
    val request = basicRequest
      .post(uri"$serverBaseUrl:$serverPort/${employeeProfileEndpoint(0)}/${employeeProfileEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(profile.trim)
    makeRequestWithRetries(request)
  }

  private def callPostEmployeeDocsTool(secret: String, caller: String, document: String)
    (using context: ActorContext[Command]): Option[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the PostEmployeeDocs('$secret') tool.")
    val request = basicRequest
      .post(uri"$serverBaseUrl:$serverPort/${employeeDocsEndpoint(0)}/${employeeDocsEndpoint(1)}?secret=$secret&caller=$caller")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  }

  private def callGetInternalDocsTool(caller: String)(using context: ActorContext[Command]): List[String] = {
    context.log.info(s"${getAgentIdFromName(caller)} is calling the GetInternalDocs() tool.")
    val request = basicRequest
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