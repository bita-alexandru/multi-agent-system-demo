package alex.demo

import agents.Agent.Command
import agents.SupervisorWorker

import io.github.cdimascio.dotenv.Dotenv
import org.apache.pekko.actor.typed.ActorSystem

val dotenv = Dotenv.load()

@main
def main(): Unit =
  val supervisorAgent: ActorSystem[Command] = ActorSystem(SupervisorWorker(), "supervisor-agent")
  val userInput = "andrei"
  supervisorAgent ! Command.Start()
end main

private def takeInput(): Option[String] =
  Some(scala.io.StdIn.readLine().trim).filter(_.nonEmpty)
end takeInput
