/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.cluster.sharding.typed.internal.Murmur2

abstract class Murmur2NoEnvelopeMessageExtractor[M](val numberOfShards: Int) extends ShardingMessageExtractor[M, M] {
  override def shardId(entityId: String): String = Murmur2.shardId(entityId, numberOfShards)
  override def unwrapMessage(message: M): M = message
}

/**
 * The murmur2 message extractor uses the same algorithm as the default kafka partitioner
 * allowing kafka partitions to be mapped to shards.
 * This can be used with the [[pekko.cluster.sharding.external.ExternalShardAllocationStrategy]] to have messages
 * processed locally.
 *
 * Extend [[Murmur2NoEnvelopeMessageExtractor]] to not use a message envelope extractor.
 */
final class Murmur2MessageExtractor[M](val numberOfShards: Int)
    extends ShardingMessageExtractor[ShardingEnvelope[M], M] {
  override def entityId(envelope: ShardingEnvelope[M]): String = envelope.entityId
  override def shardId(entityId: String): String = Murmur2.shardId(entityId, numberOfShards)
  override def unwrapMessage(envelope: ShardingEnvelope[M]): M = envelope.message
}
