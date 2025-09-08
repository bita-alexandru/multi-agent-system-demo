package alex.demo
package agents

import agents.Agent.{Command, CommandProps, makeRequestWithRetries}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import sttp.client4.quick.*

object WorkerAgent extends Agent:
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val getEmployeeCvEndpoint = dotenv.get("GET_EMPLOYEE_CV_ENDPOINT")
  private val getEmployeeIdEndpoint = dotenv.get("GET_EMPLOYEE_ID_ENDPOINT")
  private val getInternalDocsEndpoint = dotenv.get("GET_INTERNAL_DOCS_ENDPOINT")
  private val postEmployeeDocEndpoint = dotenv.get("POST_EMPLOYEE_DOC_ENDPOINT")
  private val postEmployeeProfileEndpoint = dotenv.get("POST_EMPLOYEE_PROFILE_ENDPOINT")

  def doStart(systemPrompt: Option[String], commandProps: CommandProps)(using context: ActorContext[Command]): Behavior[Command] =
    systemPrompt.foreach(context.log.info)
    Behaviors.same
  end doStart

  def doNext(systemPrompt: Option[String], commandProps: CommandProps)(using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doNext

  def doReview(systemPrompt: Option[String], commandProps: CommandProps)(using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doReview

  def doEnd(systemPrompt: Option[String], commandProps: CommandProps)(using context: ActorContext[Command]): Behavior[Command] =
    Behaviors.same
  end doEnd

  def doCallTool(systemPrompt: Option[String], commandProps: CommandProps)(using context: ActorContext[Command]): Behavior[Command] =
    systemPrompt.foreach(context.log.info)
    Behaviors.same
  end doCallTool

  private def callGetEmployeeCvTool(secret: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getEmployeeCvEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
    makeRequestWithRetries(request)
  end callGetEmployeeCvTool

  private def callGetEmployeeIdTool(secret: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getEmployeeIdEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
    makeRequestWithRetries(request)
  end callGetEmployeeIdTool

  private def callGetInternalDocsTool(using context: ActorContext[Command]): List[Option[String]] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getInternalDocsEndpoint")
      .header("Content-Type", "application/json")
    List(makeRequestWithRetries(request))
  end callGetInternalDocsTool

  private def callPostEmployeeDocTool(secret: String, filename: String, document: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$postEmployeeDocEndpoint?secret=$secret&filename=$filename")
      .header("Content-Type", "application/json")
      .body(document.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeDocTool

  private def callPostEmployeeProfileTool(secret: String, profile: String)(using context: ActorContext[Command]): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$postEmployeeProfileEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
      .body(profile.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeProfileTool

end WorkerAgent
