package alex.demo

import agents.Agent.Command
import agents.OrchestratorAgent

import io.github.cdimascio.dotenv.Dotenv
import org.apache.pekko.actor.typed.ActorSystem

val dotenv = Dotenv.load()

@main
def main(): Unit =
  val orchestratorAgent: ActorSystem[Command] = ActorSystem(OrchestratorAgent(), "orchestrator-agent")
  val userInput = "andrei"
  orchestratorAgent ! Command.Start()
end main

private def takeInput(): Option[String] =
  Some(scala.io.StdIn.readLine().trim).filter(_.nonEmpty)
end takeInput
