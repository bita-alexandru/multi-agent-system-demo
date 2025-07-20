package demo.alex.application
package agents

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object OrchestratorAgent:
  sealed trait Command
  object Start extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Start =>
          println("start")
          Behaviors.same
        case _ =>
          println("nothing")
          Behaviors.same
      }
    }
