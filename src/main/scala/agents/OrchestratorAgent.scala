package alex.demo
package agents

import agents.Agent.{Command, CommandProps, askLlm}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

object OrchestratorAgent extends Agent:
  def doStart(systemPrompt: Option[String], commandProps: CommandProps)
    (using context: ActorContext[Command]): Behavior[Command] =
    systemPrompt.foreach(context.log.info)
    val worker = context.spawn(WorkerAgent(), "worker")
    worker ! Command.CallTool()
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
    Behaviors.same
  end doCallTool

end OrchestratorAgent

