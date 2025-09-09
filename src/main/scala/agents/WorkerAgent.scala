package alex.demo
package agents

import agents.Agent.{Command, CommandProps, makeRequestWithRetries}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object WorkerAgent extends Agent:
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val employeeCvEndpoint = dotenv.get("EMPLOYEE_CV_ENDPOINT")
  private val employeeIdEndpoint = dotenv.get("EMPLOYEE_ID_ENDPOINT")
  private val employeeProfileEndpoint = dotenv.get("EMPLOYEE_PROFILE_ENDPOINT")
  private val employeeDocsEndpoint = dotenv.get("EMPLOYEE_DOCS_ENDPOINT")
  private val internalDocsEndpoint = dotenv.get("INTERNAL_DOCS_ENDPOINT")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    systemPrompt.foreach(context.log.info)
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
    systemPrompt.foreach(context.log.info)
    Behaviors.same
  end doCallTool

  private def callGetEmployeeCvTool(secret: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$employeeCvEndpoint?secret=$secret")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  end callGetEmployeeCvTool

  private def callGetEmployeeIdTool(secret: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$employeeIdEndpoint?secret=$secret")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request)
  end callGetEmployeeIdTool

  private def callPostEmployeeProfileTool(secret: String, profile: String)
    (using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$employeeProfileEndpoint?secret=$secret")
      .header("Content-Type", "text/html")
      .body(profile.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeProfileTool

  private def callPostEmployeeDocsTool(secret: String, filename: String, document: String)
    (using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$employeeDocsEndpoint?secret=$secret")
      .header("Content-Type", "text/html")
      .body(document.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeDocsTool

  private def callGetInternalDocsTool(using context: ActorContext[Command]): List[Option[String]] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$internalDocsEndpoint")
      .header("Content-Type", "text/html")
    makeRequestWithRetries(request).map: docs =>
      docs
        .split("(?i)(?=<!DOCTYPE html>)")
        .toList
        .filter(_.nonEmpty)
        .map(Some(_))
    .getOrElse(Nil)
  end callGetInternalDocsTool

end WorkerAgent
