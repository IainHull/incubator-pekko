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

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

final case class ClientDowningNodeThatIsUnreachableMultiNodeConfig(failureDetectorPuppet: Boolean)
    extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")

  commonConfig(debugConfig(on = false).withFallback(MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet)))
}

class ClientDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode1
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = true)
class ClientDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode2
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = true)
class ClientDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode3
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = true)
class ClientDowningNodeThatIsUnreachableWithFailureDetectorPuppetMultiJvmNode4
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = true)

class ClientDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = false)
class ClientDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = false)
class ClientDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = false)
class ClientDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4
    extends ClientDowningNodeThatIsUnreachableSpec(failureDetectorPuppet = false)

abstract class ClientDowningNodeThatIsUnreachableSpec(
    multiNodeConfig: ClientDowningNodeThatIsUnreachableMultiNodeConfig)
    extends MultiNodeClusterSpec(multiNodeConfig) {

  def this(failureDetectorPuppet: Boolean) =
    this(ClientDowningNodeThatIsUnreachableMultiNodeConfig(failureDetectorPuppet))

  import multiNodeConfig._

  "Client of a 4 node cluster" must {

    "be able to DOWN a node that is UNREACHABLE (killed)" taggedAs LongRunningTest in {
      val thirdAddress = address(third)
      awaitClusterUp(first, second, third, fourth)

      runOn(first) {
        // kill 'third' node
        testConductor.exit(third, 0).await
        markNodeAsUnavailable(thirdAddress)

        // mark 'third' node as DOWN
        cluster.down(thirdAddress)
        enterBarrier("down-third-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(thirdAddress))
        clusterView.members.exists(_.address == thirdAddress) should ===(false)
      }

      runOn(third) {
        enterBarrier("down-third-node")
      }

      runOn(second, fourth) {
        enterBarrier("down-third-node")

        awaitMembersUp(numberOfMembers = 3, canNotBePartOfMemberRing = Set(thirdAddress))
      }

      enterBarrier("await-completion")
    }
  }
}
