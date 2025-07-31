package alex.demo
package utils

import java.io.FileReader
import scala.util.Try
import cats.syntax.either._
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml

sealed case class SystemPrompts(start: String, next: String, review: String, end: String)

type AgentConfig = Map[String, SystemPrompts]

object AgentConfigs:
  private val systemPromptsPath: String = "src/main/resources/system-prompts.yml"

  val agentConfigs: AgentConfig = getAgentConfigs

  private def processJson(
    json: Either[ParsingFailure, Json]
  ): Either[Error, AgentConfig] =
    json
      .leftMap(err => err: Error)
      .flatMap(_.as[AgentConfig])
  end processJson

  private def readConfig: Option[List[Either[Error, AgentConfig]]] =
    Try(new FileReader(systemPromptsPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match
      case Left(err) => None
      case Right(agentConfigs) => Some(agentConfigs)
  end readConfig

  private def getAgentConfigs: AgentConfig =
    readConfig match
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, SystemPrompts])(_ ++ _)
      case None => Map.empty
  end getAgentConfigs

  def getAgentConfigByCommand(agentConfigs: AgentConfig, agent: String, command: String): Option[String] =
    for
      config <- agentConfigs.get(agent)
      prompt <- command match
        case "start" => Some(config.start)
        case "next" => Some(config.next)
        case "review" => Some(config.review)
        case "end" => Some(config.end)
    yield prompt
  end getAgentConfigByCommand

end AgentConfigs
