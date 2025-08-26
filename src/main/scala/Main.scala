package alex.demo

import agents.Agent.{Command, CommandProps}
import agents.OrchestratorAgent

import io.github.cdimascio.dotenv.Dotenv
import org.apache.pekko.actor.typed.ActorSystem

val dotenv = Dotenv.load()

// todo: 2 workers: 1 to set up bob profile based on employee data
//  (main/secondary skills, about me, superhero skills, food preferences, hobbies, managed by/manages, recruited by etc.)
//  1 to set up internal CVUP style cv based on employee's current CV
//  OR launch multiple workers to do the SAME task, and supervisor picks best output
// todo: 2 services: 1 for getting employee data (real endpoint but mock data),
//  1 for writing data on the DB (real endpoint but mock data)
// todo: user may ask for manual review, if not specified assume yes

@main
def main(): Unit =
  val orchestratorAgent: ActorSystem[Command] = ActorSystem(OrchestratorAgent(), "orchestrator-agent")
  orchestratorAgent ! Command.Start(props = CommandProps())
  orchestratorAgent ! Command.Next(props = CommandProps())
  orchestratorAgent ! Command.Review(props = CommandProps())
  orchestratorAgent ! Command.End(props = CommandProps())
  orchestratorAgent ! Command.CallTool(props = CommandProps())
end main
