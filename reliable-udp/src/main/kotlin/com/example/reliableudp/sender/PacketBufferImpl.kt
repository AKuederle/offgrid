package com.example.reliableudp.sender

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe PacketBuffer implementation using ConcurrentHashMap.
 *
 * Adapted from Quincy QUIC implementation.
 *
 * Original: https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/PacketBuffer.java
 * License: Apache 2.0 (https://github.com/protocol7/quincy/blob/master/LICENSE)
 *
 * Modifications:
 * - Ported from Java to Kotlin
 * - Simplified for connectionless UDP
 * - Added message-level operations
 */
class PacketBufferImpl : PacketBuffer {

    private val buffer = ConcurrentHashMap<Long, SentPacket>()

    override fun put(packet: SentPacket) {
        buffer[packet.sequenceNumber] = packet
    }

    override fun remove(sequenceNumber: Long): SentPacket? {
        return buffer.remove(sequenceNumber)
    }

    override fun getOlderThan(timeoutNanos: Long): List<SentPacket> {
        val now = System.nanoTime()
        return buffer.values.filter { packet ->
            now - packet.sentAt > timeoutNanos
        }
    }

    override fun removeByMessageId(messageId: Int): List<SentPacket> {
        val toRemove = buffer.values.filter { it.messageId == messageId }
        toRemove.forEach { buffer.remove(it.sequenceNumber) }
        return toRemove
    }

    override fun hasPendingPackets(messageId: Int): Boolean {
        return buffer.values.any { it.messageId == messageId }
    }

    override val size: Int
        get() = buffer.size
}
