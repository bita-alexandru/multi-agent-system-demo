package alex.demo
package agents

import agents.BaseAgent.CommandProps

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}

object OrchestratorAgent extends BaseAgent:
  def doStart(context: ActorContext[Command], systemPrompt: Option[String], commandProps: CommandProps): Behavior[Command] =
    println(systemPrompt)
    Behaviors.same
  end doStart

  private def takeInput(): Option[String] =
    Option(scala.io.StdIn.readLine().trim).filter(_.nonEmpty)
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

end OrchestratorAgent

