package protocols

import akka.actor.testkit.typed.scaladsl._
import akka.actor.typed.ActorRef
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}
import protocols.TransactorState.{GetHead, PrintList, SaveReplyTo, SessionState}

trait TransactorStateSpec extends FunSuite with MustMatchers with PropertyChecks {

  test("A TransactorState must store in list") {

    val testkit = BehaviorTestKit(TransactorState[SessionState[String]](List()))

    testkit.ref ! SaveReplyTo[String]("Sofia")
    testkit.runOne()
    testkit.ref ! SaveReplyTo[String]("Gaia")
    testkit.runOne()
    testkit.ref ! SaveReplyTo[String]("Vincenzo")
    testkit.runOne()

    testkit.ref ! SaveReplyTo[String]("Antonella")
    testkit.runOne()
    testkit.ref ! PrintList[String]()
    testkit.runOne()

    val sessionInbox = TestInbox[String]()

    testkit.ref ! GetHead[String](sessionInbox.ref)
    testkit.runOne()

    val message = sessionInbox.receiveMessage()

    message must be("Sofia")

    testkit.ref ! PrintList[String]()
    testkit.runOne()
  }


}
