package com.example.reliableudp.receiver

import com.example.reliableudp.ReliableUdpConstants
import com.example.reliableudp.protocol.AckFrame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AckQueue implementation using Kotlin Channel.
 *
 * Adapted from Quincy QUIC implementation.
 *
 * Original: https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/AckQueue.java
 * License: Apache 2.0 (https://github.com/protocol7/quincy/blob/master/LICENSE)
 *
 * Modifications:
 * - Ported from Java to Kotlin
 * - Uses Kotlin Channel instead of BlockingQueue
 * - Simplified for connectionless UDP
 */
class AckQueueImpl : AckQueue {

    private val pendingAcks = mutableSetOf<Long>()
    private val mutex = Mutex()
    private var lastReceiveTime = System.nanoTime()

    override suspend fun enqueue(sequenceNumber: Long) {
        mutex.withLock {
            pendingAcks.add(sequenceNumber)
            lastReceiveTime = System.nanoTime()
        }
    }

    override suspend fun drain(): AckFrame? {
        return mutex.withLock {
            if (pendingAcks.isEmpty()) {
                null
            } else {
                val packets = pendingAcks.toList()
                val ackDelayNanos = System.nanoTime() - lastReceiveTime
                val ackDelayMicros = (ackDelayNanos / 1000).toInt().coerceIn(0, 65535)

                pendingAcks.clear()

                AckFrame.fromPacketNumbers(packets, ackDelayMicros)
            }
        }
    }

    override val pendingCount: Int
        get() = pendingAcks.size
}
