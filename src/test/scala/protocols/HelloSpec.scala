package protocols

import akka.Done
import akka.actor.testkit.typed.Effect
import akka.actor.testkit.typed.scaladsl._
import akka.actor.typed._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.duration._

trait HelloSpec extends FunSuite with MustMatchers with PropertyChecks {

  test("A Hello Behaviour must greet") {

    val testkit = BehaviorTestKit(HelloWorld())
    val sessionInbox = TestInbox[String]()

    testkit.ref ! "Vincenzo"
    testkit.runOne()
//    val message = sessionInbox.receiveMessage()
    testkit.ref ! "Antonella"
    testkit.runOne()
  }


}
