package alex.demo

import agents.BaseAgent.CommandProps
import agents.OrchestratorAgent

import org.apache.pekko.actor.typed.ActorSystem

@main
def main(): Unit =
  val orchestratorAgent: ActorSystem[OrchestratorAgent.Command] =
    ActorSystem(OrchestratorAgent(), "MainActorSystem")
  orchestratorAgent ! OrchestratorAgent.Command.Start(props = CommandProps())
  orchestratorAgent ! OrchestratorAgent.Command.Next(props = CommandProps())
  orchestratorAgent ! OrchestratorAgent.Command.Review(props = CommandProps())
  orchestratorAgent ! OrchestratorAgent.Command.End(props = CommandProps())
end main
