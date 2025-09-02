package alex.demo
package agents

import agents.Agent.{Command, CommandProps, askLlm}

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

object OrchestratorAgent extends Agent:
  def doStart(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    print(askLlm("describe tic tac toe in a few words"))
    Behaviors.same
  end doStart

  private def takeInput(): Option[String] =
    Some(scala.io.StdIn.readLine().trim).filter(_.nonEmpty)
  end takeInput

  def doNext(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    Behaviors.same
  end doNext

  def doReview(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    Behaviors.same
  end doReview

  def doEnd(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    Behaviors.same
  end doEnd

  def doCallTool(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    Behaviors.same
  end doCallTool

end OrchestratorAgent

