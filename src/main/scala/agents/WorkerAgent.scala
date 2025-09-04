package alex.demo
package agents

import agents.Agent.makeRequestWithRetries

import sttp.client4.quick.*

object WorkerAgent:
  private val serverBaseUrl = dotenv.get("SERVER_BASE_URL")
  private val serverPort = dotenv.get("SERVER_PORT")
  private val getEmployeeCvEndpoint = dotenv.get("GET_EMPLOYEE_CV_ENDPOINT")
  private val getEmployeeIdEndpoint = dotenv.get("GET_EMPLOYEE_ID_ENDPOINT")
  private val getInternalDocsEndpoint = dotenv.get("GET_INTERNAL_DOCS_ENDPOINT")
  private val postEmployeeDocEndpoint = dotenv.get("POST_EMPLOYEE_DOC_ENDPOINT")
  private val postEmployeeProfileEndpoint = dotenv.get("POST_EMPLOYEE_PROFILE_ENDPOINT")

  private def callGetEmployeeCvTool(secret: String): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getEmployeeCvEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
    makeRequestWithRetries(request)
  end callGetEmployeeCvTool

  private def callGetEmployeeIdTool(secret: String): Option[String] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getEmployeeIdEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
    makeRequestWithRetries(request)
  end callGetEmployeeIdTool

  private def callGetInternalDocsTool: List[Option[String]] =
    val request = quickRequest
      .get(uri"$serverBaseUrl:$serverPort/$getInternalDocsEndpoint")
      .header("Content-Type", "application/json")
    List(makeRequestWithRetries(request))
  end callGetInternalDocsTool

  private def callPostEmployeeDocTool(secret: String, filename: String, document: String): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$postEmployeeDocEndpoint?secret=$secret&filename=$filename")
      .header("Content-Type", "application/json")
      .body(document.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeDocTool

  private def callPostEmployeeProfileTool(secret: String, profile: String): Option[String] =
    val request = quickRequest
      .post(uri"$serverBaseUrl:$serverPort/$postEmployeeProfileEndpoint?secret=$secret")
      .header("Content-Type", "application/json")
      .body(profile.trim)
    makeRequestWithRetries(request)
  end callPostEmployeeProfileTool

end WorkerAgent
