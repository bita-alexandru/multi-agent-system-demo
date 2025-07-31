package alex.demo

import agents.BaseAgent.{Command, CommandProps}
import agents.OrchestratorAgent

import org.apache.pekko.actor.typed.ActorSystem

@main
def main(): Unit =
  val orchestratorAgent: ActorSystem[Command] =
    ActorSystem(OrchestratorAgent(), "orchestrator-agent")
  orchestratorAgent ! Command.Start(props = CommandProps())
  orchestratorAgent ! Command.Next(props = CommandProps())
  orchestratorAgent ! Command.Review(props = CommandProps())
  orchestratorAgent ! Command.End(props = CommandProps())
  orchestratorAgent ! Command.CallTool(props = CommandProps())
end main
