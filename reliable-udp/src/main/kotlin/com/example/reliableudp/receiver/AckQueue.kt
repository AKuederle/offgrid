package com.example.reliableudp.receiver

import com.example.reliableudp.protocol.AckFrame

/**
 * Queues received packet numbers for batched ACK generation.
 *
 * Adapted from Quincy QUIC implementation.
 *
 * Original: https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/AckQueue.java
 * License: Apache 2.0 (https://github.com/protocol7/quincy/blob/master/LICENSE)
 *
 * Modifications:
 * - Ported from Java to Kotlin
 * - Simplified for connectionless UDP (removed encryption levels)
 * - Uses Kotlin coroutines instead of BlockingQueue
 * - Added range coalescing for efficient SACK generation
 */
interface AckQueue {
    /**
     * Record a received packet for future acknowledgment.
     *
     * @param sequenceNumber The packet sequence number that was received
     */
    suspend fun enqueue(sequenceNumber: Long)

    /**
     * Drain all pending packet numbers and generate ACK frame.
     *
     * Coalesces individual packet numbers into contiguous ranges.
     *
     * @return AckFrame containing all pending acknowledgments, or null if empty
     */
    suspend fun drain(): AckFrame?

    /**
     * Number of packets pending acknowledgment.
     */
    val pendingCount: Int
}
