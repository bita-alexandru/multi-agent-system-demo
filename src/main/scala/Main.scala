package demo.alex.application

import agents.OrchestratorAgent

import org.apache.pekko.actor.typed.ActorSystem

@main
def main(): Unit =
  println("hello")
  val orchestratorAgent: ActorSystem[OrchestratorAgent.Command] =
    ActorSystem(OrchestratorAgent(), "Main")

  orchestratorAgent ! OrchestratorAgent.Start
