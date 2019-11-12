package protocols

import akka.actor.typed._
import akka.actor.typed.scaladsl._

object SelectiveReceive {
    var bSize: Int = 0
    /**
      * @return A behavior that stashes incoming messages unless they are handled
      *         by the underlying `initialBehavior`
      * @param bufferSize Maximum number of messages to stash before throwing a `StashOverflowException`
      *                   Note that 0 is a valid size and means no buffering at all (ie all messages should
      *                   always be handled by the underlying behavior)
      * @param initialBehavior Behavior to decorate
      * @tparam T Type of messages
      *
      * Hint: Implement an [[ExtensibleBehavior]], use a [[StashBuffer]] and [[Behavior]] helpers such as `start`,
      * `validateAsInitial`, `interpretMessage`,`canonicalize` and `isUnhandled`.
      */
    def apply[T](bufferSize: Int, initialBehavior: Behavior[T]): Behavior[T] = {
        bSize = bufferSize
        Behaviors.setup[T](_ => new ExtBehavior[T](initialBehavior, initialBehavior))
    }
}

class ExtBehavior[T](initial: Behavior[T], behavior: Behavior[T]) extends ExtensibleBehavior[T] {
    import SelectiveReceive._
    var buffer = StashBuffer[T](bSize)
    override def receive(ctx: TypedActorContext[T], msg: T): Behavior[T] = {
        import akka.actor.typed.Behavior.{ start, canonicalize, validateAsInitial,
            interpretMessage, unhandled }
        try {
            val started = validateAsInitial(start(behavior, ctx))
            val next = interpretMessage(started, ctx, msg)
            if (next == unhandled) {
                if (buffer.isFull) {
                    throw new StashOverflowException("")
                } else {
                    buffer.stash(msg)
                    next
                }
            } else {
                buffer.unstashAll(ctx.asScala, new ExtBehavior(initial, canonicalize(next, started, ctx)))
            }
        } catch {
            case ex: StashOverflowException => throw ex
            case _: Exception =>
                new ExtBehavior(initial, validateAsInitial(start(initial, ctx)))
        }
    }

    override def receiveSignal(ctx: TypedActorContext[T], msg: Signal): Behavior[T] = {
        Behavior.same
    }

}