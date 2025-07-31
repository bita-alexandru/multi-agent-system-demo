package alex.demo
package agents

import agents.BaseAgent.CommandProps
import utils.AgentConfigs
import utils.AgentConfigs.getAgentConfigByCommand

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

abstract class BaseAgent:
  enum Command(val commandProps: CommandProps):
    case Start(props: CommandProps) extends Command(props)
    case Next(props: CommandProps) extends Command(props)
    case Review(props: CommandProps) extends Command(props)
    case End(props: CommandProps) extends Command(props)

  def apply(): Behavior[Command] =
    Behaviors.setup: context =>
      Behaviors.receiveMessage:
        case Command.Start(props) =>
          doStart(context, getSystemPrompts(context, Command.Start(props)), props)
        case Command.Next(props) =>
          doNext(context, getSystemPrompts(context, Command.Next(props)), props)
        case Command.Review(props) =>
          doReview(context, getSystemPrompts(context, Command.Review(props)), props)
        case Command.End(props) =>
          doEnd(context, getSystemPrompts(context, Command.End(props)), props)
        case null => Behaviors.same
  end apply

  private def getSystemPrompts(context: ActorContext[Command], command: Command): Option[String] =
    val commandStr = command match
      case Command.Start(_) => "start"
      case Command.Next(_) => "next"
      case Command.Review(_) => "review"
      case Command.End(_) => "end"
    getAgentConfigByCommand(AgentConfigs.agentConfigs, context.system.name, commandStr)
  end getSystemPrompts

  def doStart(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doNext(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doReview(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]

  def doEnd(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command]
end BaseAgent

object BaseAgent:
  sealed case class CommandProps(
    prompt: Option[String] = None,
    from: Option[ActorRef[BaseAgent]] = None,
    contacts: Option[List[ActorRef[BaseAgent]]] = None
  )
end BaseAgent
