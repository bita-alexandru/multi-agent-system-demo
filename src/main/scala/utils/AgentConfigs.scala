package alex.demo
package utils

import agents.BaseAgent.Command

import java.io.FileReader
import scala.util.Try
import cats.syntax.either.*
import io.circe.*
import io.circe.yaml
import io.circe.Decoder

given Decoder[SystemPrompts] = Decoder.forProduct5(
  "start",
  "next",
  "review",
  "end",
  "call-tool"
)(SystemPrompts.apply)

sealed case class SystemPrompts(
  start: String,
  next: String,
  review: String,
  end: String,
  callTools: String
)

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

  private def readConfigs: Option[List[Either[Error, AgentConfig]]] =
    Try(new FileReader(systemPromptsPath))
      .toEither
      .map(fileReader => yaml.parser.parseDocuments(fileReader).toList)
      .map(_.map(processJson)) match
      case Left(err) => None
      case Right(configs) =>
        Some(configs)
  end readConfigs

  private def getAgentConfigs: AgentConfig =
    readConfigs match
      case Some(configs) =>
        configs.collect { case Right(cfg) => cfg }.foldLeft(Map.empty[String, SystemPrompts])(_ ++ _)
      case None => Map.empty
  end getAgentConfigs

  def getAgentConfigByCommand(agentConfigs: AgentConfig, agentId: String, command: Command): Option[String] =
    for
      config <- agentConfigs.get(agentId)
      prompt <- command match
        case Command.Start(_) => Some(config.start)
        case Command.Next(_) => Some(config.next)
        case Command.Review(_) => Some(config.review)
        case Command.End(_) => Some(config.end)
        case Command.CallTool(_) => Some(config.callTools)
    yield prompt
  end getAgentConfigByCommand

end AgentConfigs
