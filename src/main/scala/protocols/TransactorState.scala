package protocols

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import akka.Done
import akka.actor.ActorContext

object TransactorState {

    sealed trait SessionState[T] extends Product with Serializable
    final case class SaveReplyTo[T](value: T) extends SessionState[T]
    final case class PrintList[T]() extends SessionState[T]
    final case class GetHead[T](actorRef: ActorRef[T]) extends SessionState[T]
    final case class GetSize[T](actorRef: ActorRef[T]) extends SessionState[T]

    def apply[T](ts: List[T]): Behavior[T] = {
          TransactorState.getBehavior(ts).narrow
    }

    def getBehavior[T](list:List[T]): Behavior[T] =
    {
        Behaviors.receive[T]({
            case (ctx, SaveReplyTo(value:T)) => {
                val newList:List[T] = list :+ value
                Behaviors.stopped[T]
                TransactorState.getBehavior(newList)
            }
            case (ctx, PrintList()) => {
                println(list)
                Behaviors.same[T]
            }
            case (ctx, GetHead(actorRef:ActorRef[T])) => {
                val value:T = list.head
                actorRef.narrow.tell(value)
                Behaviors.stopped[T]
                TransactorState.apply(list.tail)
            }
            case _ => {
                println(s"empty")
                Behaviors.same
            }
        })
    }

}
