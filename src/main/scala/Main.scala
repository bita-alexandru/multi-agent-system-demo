package alex.demo

import agents.Agent.{Command, CommandProps, Label}
import agents.SupervisorAgent

import io.github.cdimascio.dotenv.Dotenv
import org.apache.pekko.actor.typed.ActorSystem

val dotenv = Dotenv.load()

@main
def main(): Unit = {
  val supervisorAgent: ActorSystem[Command] = ActorSystem(SupervisorAgent(), Label.Supervisor)
  val userInput: String = Iterator
    .continually {
      print("Please input your secret code: ")
      takeInput()
    }
    .collectFirst { case Some(input) => input }
    .get

  supervisorAgent ! Command.Start(props = CommandProps(input = List(userInput)))
}

def takeInput(): Option[String] =
  Some(scala.io.StdIn.readLine().trim).filter(_.nonEmpty)
