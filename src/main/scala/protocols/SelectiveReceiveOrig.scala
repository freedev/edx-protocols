package protocols

import akka.actor.typed.Behavior.{canonicalize, interpretMessage, start, validateAsInitial}
import akka.actor.typed.{Behavior, BehaviorInterceptor, PreRestart, Signal, TypedActorContext}
import akka.actor.typed.scaladsl.{ActorContext, _}
import akka.event.Logging

object SelectiveReceiveOrig {
    /**
      * @return A behavior that stashes incoming messages unless they are handled
      *         by the underlying `initialBehavior`
      * @param bufferSize Maximum number of messages to stash before throwing a `StashOverflowException`
      *                   Note that 0 is a valid size and means no buffering at all (ie all messages should
      *                   always be handled by the underlying behavior)
      * @param initialBehavior Behavior to decorate
      * @tparam T Type of messages
      *
      * Hint: Use [[Behaviors.intercept]] to intercept messages sent to the `initialBehavior` with
      *       the `Interceptor` defined below
      */
    def apply[T](bufferSize: Int, initialBehavior: Behavior[T]): Behavior[T] =
        {
            Behaviors.setup[T] { ctx =>
                Behaviors.intercept(new Interceptor[T](bufferSize))(initialBehavior)
            }
        }

    /**
      * An interceptor that stashes incoming messages unless they are handled by the target behavior.
      *
      * @param bufferSize Stash buffer size
      * @tparam T Type of messages
      *
      * Hint: Ue a [[StashBuffer]] and [[Behavior]] helpers such as `same`
      * and `isUnhandled`.
      */
    private class Interceptor[T](bufferSize: Int) extends BehaviorInterceptor[T, T] {
        import BehaviorInterceptor.{ReceiveTarget, SignalTarget}

        val buffer = StashBuffer[T](bufferSize)
        /**
          * @param ctx Actor context
          * @param msg Incoming message
          * @param target Target (intercepted) behavior
          */
        def aroundReceive(ctx: TypedActorContext[T], msg: T, target: ReceiveTarget[T]): Behavior[T] = {

            try {
                println(msg)
                val next = target(ctx, msg)
//                val started = validateAsInitial(target(ctx, msg))
//                val next = interpretMessage(started, ctx, msg)
                if (Behavior.isUnhandled(next)) {
                    if (buffer.isFull) {
                        throw new StashOverflowException("")
                    } else {
                        buffer.stash(msg)
                        next
                    }
                } else {
//                    buffer.unstashAll(ctx.asScala, new ExtBehavior(initial, canonicalize(next, started, ctx)))
                    buffer.unstashAll(ctx.asScala, start(next, ctx))
                    Behaviors.empty
                }
            } catch {
                case ex: StashOverflowException => throw ex
//                case _: Exception =>
//                    new ExtBehavior(initial, validateAsInitial(start(initial, ctx)))
            }


//            if (bufferSize == 0)
//                throw new StashOverflowException("bufferSize == 0")
//            if (behavior.equals(Behaviors.unhandled)) {
//                buffer.stash(msg)
//                println(" Behaviors.unhandled " + target + " ---- " + msg)
//                Behaviors.same
//            } else {
//                buffer.unstashAll(ctx.asScala, behavior)
//                println(" NOT Behaviors.unhandled " + target + " ---- " + msg)
//                Behaviors.empty
//            }
        }

        // Forward signals to the target behavior
        def aroundSignal(ctx: TypedActorContext[T], signal: Signal, target: SignalTarget[T]): Behavior[T] =
            target(ctx, signal)

    }

}
