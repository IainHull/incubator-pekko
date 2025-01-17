/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.actor;

import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;

// #import
import org.apache.pekko.actor.Actor;
import org.apache.pekko.actor.IndirectActorProducer;
// #import

public class DependencyInjectionDocTest extends AbstractJavaTest {

  public static class TheActor extends AbstractActor {

    final String s;

    public TheActor(String s) {
      this.s = s;
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              String.class,
              msg -> {
                getSender().tell(s, getSelf());
              })
          .build();
    }
  }

  static ActorSystem system = null;

  @BeforeClass
  public static void beforeClass() {
    system = ActorSystem.create("DependencyInjectionDocTest");
  }

  @AfterClass
  public static void afterClass() {
    TestKit.shutdownActorSystem(system);
  }

  // this is just to make the test below a tiny fraction nicer
  private ActorSystem getContext() {
    return system;
  }

  static
  // #creating-indirectly
  class DependencyInjector implements IndirectActorProducer {
    final Object applicationContext;
    final String beanName;

    public DependencyInjector(Object applicationContext, String beanName) {
      this.applicationContext = applicationContext;
      this.beanName = beanName;
    }

    @Override
    public Class<? extends Actor> actorClass() {
      return TheActor.class;
    }

    @Override
    public TheActor produce() {
      TheActor result;
      // #obtain-fresh-Actor-instance-from-DI-framework
      result = new TheActor((String) applicationContext);
      // #obtain-fresh-Actor-instance-from-DI-framework
      return result;
    }
  }
  // #creating-indirectly

  @Test
  public void indirectActorOf() {
    final String applicationContext = "...";
    // #creating-indirectly

    final ActorRef myActor =
        getContext()
            .actorOf(
                Props.create(DependencyInjector.class, applicationContext, "TheActor"), "TheActor");
    // #creating-indirectly
    new TestKit(system) {
      {
        myActor.tell("hello", getRef());
        expectMsgEquals("...");
      }
    };
  }
}
