package protocols

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import akka.Done
import akka.actor.ActorContext

object HelloWorld {

    def apply(): Behavior[String] =
    {
        println("started")

        Behaviors.receive({
            case (ctx, name) => {
                println(s"Hello $name")
                Behaviors.same
            }
            case _ => {
                println(s"empty")
                Behaviors.same
            }
        })
    }

}

