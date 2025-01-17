/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.scaladsl.CurrentEventsByPersistenceIdQuery
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.typed.scaladsl.ReplicatedEventSourcing
import pekko.persistence.typed.scaladsl.Effect
import pekko.persistence.typed.scaladsl.EventSourcedBehavior
import pekko.stream.scaladsl.Sink
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpecLike

object MultiJournalReplicationSpec {

  object Actor {
    sealed trait Command
    case class GetState(replyTo: ActorRef[Set[String]]) extends Command
    case class StoreMe(text: String, ack: ActorRef[Done]) extends Command

    private val writeJournalPerReplica = Map("R1" -> "journal1.journal", "R2" -> "journal2.journal")
    def apply(entityId: String, replicaId: String): Behavior[Command] = {
      ReplicatedEventSourcing
        .perReplicaJournalConfig(
          ReplicationId("MultiJournalSpec", entityId, ReplicaId(replicaId)),
          Map(ReplicaId("R1") -> "journal1.query", ReplicaId("R2") -> "journal2.query"))(replicationContext =>
          EventSourcedBehavior[Command, String, Set[String]](
            replicationContext.persistenceId,
            Set.empty[String],
            (state, command) =>
              command match {
                case GetState(replyTo) =>
                  replyTo ! state
                  Effect.none
                case StoreMe(evt, ack) =>
                  Effect.persist(evt).thenRun(_ => ack ! Done)
              },
            (state, event) => state + event))
        .withJournalPluginId(writeJournalPerReplica(replicaId))
    }
  }

  def separateJournalsConfig: Config = ConfigFactory.parseString(s"""
    journal1 {
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      query = $${pekko.persistence.testkit.query}
    }
    journal2 {
      journal.class = "${classOf[PersistenceTestKitPlugin].getName}"
      query = $${pekko.persistence.testkit.query}
    }
    """).withFallback(ConfigFactory.load()).resolve()

}

class MultiJournalReplicationSpec
    extends ScalaTestWithActorTestKit(MultiJournalReplicationSpec.separateJournalsConfig)
    with AnyWordSpecLike
    with LogCapturing
    with Eventually {
  import MultiJournalReplicationSpec._
  val ids = new AtomicInteger(0)
  def nextEntityId = s"e-${ids.getAndIncrement()}"
  "ReplicatedEventSourcing" should {
    "support one journal per replica" in {

      val r1 = spawn(Actor("id1", "R1"))
      val r2 = spawn(Actor("id1", "R2"))

      val probe = createTestProbe[Any]()
      r1 ! Actor.StoreMe("r1 m1", probe.ref)
      probe.expectMessage(Done)

      r2 ! Actor.StoreMe("r2 m1", probe.ref)
      probe.expectMessage(Done)

      eventually {
        val probe = createTestProbe[Set[String]]()
        r1 ! Actor.GetState(probe.ref)
        probe.receiveMessage() should ===(Set("r1 m1", "r2 m1"))

        r2 ! Actor.GetState(probe.ref)
        probe.receiveMessage() should ===(Set("r1 m1", "r2 m1"))
      }

      val readJournal1 = PersistenceQuery(system).readJournalFor[CurrentEventsByPersistenceIdQuery]("journal1.query")
      val readJournal2 = PersistenceQuery(system).readJournalFor[CurrentEventsByPersistenceIdQuery]("journal2.query")

      val eventsForJournal1 =
        readJournal1
          .currentEventsByPersistenceId("MultiJournalSpec|id1|R1", 0L, Long.MaxValue)
          .runWith(Sink.seq)
          .futureValue
      eventsForJournal1.map(_.event).toSet should ===(Set("r1 m1", "r2 m1"))

      val eventsForJournal2 =
        readJournal2
          .currentEventsByPersistenceId("MultiJournalSpec|id1|R2", 0L, Long.MaxValue)
          .runWith(Sink.seq)
          .futureValue
      eventsForJournal2.map(_.event).toSet should ===(Set("r1 m1", "r2 m1"))

    }
  }
}
