package protocols

import akka.actor.typed.Behavior.start
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.impl.fusing.Batch
import akka.util.Timeout
import protocols.SelectiveReceiveOrig.Interceptor

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object Transactor {

    sealed trait PrivateCommand[T] extends Product with Serializable
    final case class Committed[T](session: ActorRef[Session[T]], value: T) extends PrivateCommand[T]
    final case class RolledBack[T](session: ActorRef[Session[T]]) extends PrivateCommand[T]

    sealed trait Command[T] extends PrivateCommand[T]
    final case class Begin[T](replyTo: ActorRef[ActorRef[Session[T]]]) extends Command[T]
    final case class Timeout[T](replyTo: ActorRef[ActorRef[Session[T]]]) extends Command[T]

    sealed trait Session[T] extends Product with Serializable
    final case class Extract[T, U](f: T => U, replyTo: ActorRef[U]) extends Session[T]
    final case class Modify[T, U](f: T => T, id: Long, reply: U, replyTo: ActorRef[U]) extends Session[T]
    final case class Commit[T, U](reply: U, replyTo: ActorRef[U]) extends Session[T]
    final case class Rollback[T]() extends Session[T]
    final case class Stopped[T]() extends Session[T]

    sealed trait State[T]
    case class AwaitingSession[T]() extends State[T]
    case class SessionEstablished[T](replyTo: ActorRef[ActorRef[Session[T]]]) extends State[T]

    def parentBuilder[T](value: T, sessionTimeout: FiniteDuration): Behavior[PrivateCommand[T]] = {
        println("Entering  parentBuilder value:" + value + " sessionTimeout:" + sessionTimeout)
            Behaviors.receive[PrivateCommand[T]] {
                case (ctx, Committed(session, newValue)) => {
                    println("commitedBuilder Committed!!!")
//                    session ! Stopped[T]()
                    parentBuilder(newValue, sessionTimeout)
                }
                case (ctx, RolledBack(session)) => {
                    println(s"commitedBuilder RolledBack!!! $value")
//                    session.narrow.tell(Stopped[T]())
                    ctx.stop(session)
                    Behaviors.empty[PrivateCommand[T]]
                }
                case (ctx, Begin(replyTo)) => {
                    println("Begin!!!")
                    val sh = sessionHandler(value, ctx.self, Set(), sessionTimeout)
                    val actorRef = ctx.spawnAnonymous(sh)
                    ctx.watch(actorRef)

                    replyTo ! actorRef
                    ctx.scheduleOnce(sessionTimeout, ctx.self, RolledBack(actorRef))

                    Behaviors.same[PrivateCommand[T]]
                }
                case (ctx, Timeout(replyTo)) => {
                    Behaviors.stopped[PrivateCommand[T]]
                }
                case (ctx, _) => {
                    println("Others!!!")
                    idle[T](value, sessionTimeout)
                    Behaviors.same[PrivateCommand[T]]
                }
            }
    }

    /**
      * @return A behavior that accepts public [[Command]] messages. The behavior
      *         should be wrapped in a [[SelectiveReceive]] decorator (with a capacity
      *         of 30 messages) so that beginning new sessions while there is already
      *         a currently running session is deferred to the point where the current
      *         session is terminated.
      * @param value Initial value of the transactor
      * @param sessionTimeout Delay before rolling back the pending modifications and
      *                       terminating the session
      */
    def apply[T](value: T, sessionTimeout: FiniteDuration): Behavior[PrivateCommand[T]] =
    {
        SelectiveReceive.apply(30, parentBuilder(value, sessionTimeout))
    }

    /**
      * @return A behavior that defines how to react to any [[PrivateCommand]] when the transactor
      *         has no currently running session.
      *         [[Committed]] and [[RolledBack]] messages should be ignored, and a [[Begin]] message
      *         should create a new session.
      *
      * @param value Value of the transactor
      * @param sessionTimeout Delay before rolling back the pending modifications and
      *                       terminating the session
      *
      * Hints:
      *   - When a [[Begin]] message is received, an anonymous child actor handling the session should be spawned,
      *   - In case the child actor is terminated, the session should be rolled back,
      *   - When `sessionTimeout` expires, the session should be rolled back,
      *   - After a session is started, the next behavior should be [[inSession]],
      *   - Messages other than [[Begin]] should not change the behavior.
      */
    private def idle[T](value: T, sessionTimeout: FiniteDuration): Behavior[PrivateCommand[T]] = {
        Behaviors.receive[PrivateCommand[T]] {
            case (ctx, Committed(session, value)) => {
                Behaviors.same[PrivateCommand[T]]
            }
            case (ctx, RolledBack(session)) => {
                Behaviors.same[PrivateCommand[T]]
            }
            case (ctx, _) => {
                ctx.log.info("idle: message.... ")
                Behaviors.same[PrivateCommand[T]]
            }
        }
    }

    /**
      * @return A behavior handling [[Session]] messages. See in the instructions
      *         the precise semantics that each message should have.
      *
      * @param currentValue The session’s current value
      * @param commit Parent actor reference, to send the [[Committed]] message to
      * @param done Set of already applied [[Modify]] messages
      */
    private def sessionHandler[T](currentValue: T, commit: ActorRef[PrivateCommand[T]], done: Set[Long], sessionTimeout: FiniteDuration): Behavior[Session[T]] =
    {
        println("Entering  sessionHandler currentValue " + currentValue + " commit " + commit + " done " + done)
        Behaviors.receive[Session[T]] {
            case (ctx, Extract(f, replyTo)) => {
                println("Received Extract " + f(currentValue) + " " + replyTo)
                val ret = f(currentValue)
                replyTo.narrow.tell(ret)
                Behaviors.same[Session[T]]
            }
            case (ctx, Stopped()) => {
                println("Received Stopped ")
                Behaviors.stopped
            }
            case (ctx, Modify(f, id, reply, replyTo)) => {
                println("Received Modify " + f + " " + id + " " + reply + " " + replyTo)
                if (!done.contains(id)) {
                    val ret = f(currentValue)
                    println("Received Modify - apply function " + f + " returns " + ret)
                    replyTo.narrow.tell(reply)
                    Behaviors.stopped
                    sessionHandler(ret, commit, (done + id), sessionTimeout)
                } else {
                    println("Received Modify - done already contains " + id + " and returns currentValue " + currentValue)
                    replyTo.narrow.tell(reply)
                    Behaviors.same[Session[T]]
                }
            }
            case (ctx, Commit(reply, replyTo)) => {
                println("Received Commit " + reply + " " + replyTo)
                replyTo.narrow.tell(reply)
                if (commit != null) {
                    commit ! Committed(ctx.self, currentValue)
                }
                Behaviors.empty[Session[T]]
            }
            case (ctx, Rollback()) => {
                println("Received Rollback!!!")
                if (commit != null) {
                    commit ! RolledBack(ctx.self)
                }
                Behaviors.empty[Session[T]]
            }
            case (ctx, _) => {
                println("Received unhandled type")
                Behaviors.same[Session[T]]
            }
        }
    }

    private class Interceptor[T](bufferSize: Int) extends BehaviorInterceptor[T, T] {
        import BehaviorInterceptor.{ReceiveTarget, SignalTarget}

        var buffer = List[T]()
        /**
          * @param ctx Actor context
          * @param msg Incoming message
          * @param target Target (intercepted) behavior
          */
        def aroundReceive(ctx: TypedActorContext[T], msg: T, target: ReceiveTarget[T]): Behavior[T] = {
            try {
                println(msg)
                val next = target(ctx, msg)
                if (Behavior.isUnhandled(next)) {
                    if (buffer.size == bufferSize) {
                        throw new StashOverflowException("")
                    } else {
                        buffer = msg :: buffer
                        next
                    }
                } else {
                    //                    buffer.unstashAll(ctx.asScala, new ExtBehavior(initial, canonicalize(next, started, ctx)))
                    buffer.head
                    Behaviors.empty
                }
            } catch {
                case ex: StashOverflowException => throw ex
            }
        }

        // Forward signals to the target behavior
        def aroundSignal(ctx: TypedActorContext[T], signal: Signal, target: SignalTarget[T]): Behavior[T] =
            target(ctx, signal)

    }


}
