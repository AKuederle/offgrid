/**
 * API Contract: Internal Components
 *
 * Feature: 003-reliable-transport
 * Module: :reliable-udp
 *
 * Internal interfaces for the reliability layer components.
 * These are not part of the public API but define contracts for testability.
 */

package com.example.reliableudp.internal

import com.example.reliableudp.DeliveryResult
import java.net.InetSocketAddress

// ============================================================================
// Sender Components
// ============================================================================

/**
 * Tracks sent packets awaiting acknowledgment.
 *
 * Thread-safe. Supports concurrent access from send and ACK processing paths.
 */
interface PacketBuffer {
    /**
     * Store a sent packet for tracking.
     */
    fun put(packet: SentPacket)

    /**
     * Remove a packet when acknowledged.
     * @return The packet if found, null otherwise
     */
    fun remove(sequenceNumber: Long): SentPacket?

    /**
     * Get all packets older than the given timeout.
     * Does NOT remove them (caller decides whether to retransmit or fail).
     */
    fun getOlderThan(timeoutNanos: Long): List<SentPacket>

    /**
     * Remove and return all packets for a message.
     * Used when message delivery fails.
     */
    fun removeByMessageId(messageId: Int): List<SentPacket>

    /**
     * Check if any packets are pending for a message.
     */
    fun hasPendingPackets(messageId: Int): Boolean

    /**
     * Current number of packets in buffer.
     */
    val size: Int
}

/**
 * Fragments large messages into MTU-sized packets.
 */
interface FragmentSender {
    /**
     * Fragment a message into packets ready for sending.
     *
     * @param messageId Unique ID for this message
     * @param payload Complete message payload
     * @param destination Target address
     * @return List of packets to send (1 if no fragmentation needed)
     */
    fun fragment(
        messageId: Int,
        payload: ByteArray,
        destination: InetSocketAddress
    ): List<SentPacket>
}

/**
 * Manages retransmission timing using PTO.
 */
interface RetransmitTimer {
    /**
     * Start the retransmit check loop.
     */
    fun start()

    /**
     * Stop the retransmit loop.
     */
    fun stop()

    /**
     * Callback when a packet needs retransmission.
     */
    var onRetransmit: ((SentPacket) -> Unit)?

    /**
     * Callback when max retries exceeded.
     */
    var onMaxRetriesExceeded: ((SentPacket) -> Unit)?
}

// ============================================================================
// Receiver Components
// ============================================================================

/**
 * Queues received packet numbers for batched ACK generation.
 */
interface AckQueue {
    /**
     * Record a received packet.
     */
    suspend fun enqueue(sequenceNumber: Long)

    /**
     * Drain all pending packet numbers and generate ACK frame.
     * @return AckFrame if any packets pending, null otherwise
     */
    suspend fun drain(): AckFrame?

    /**
     * Number of packets pending acknowledgment.
     */
    val pendingCount: Int
}

/**
 * Reassembles message fragments.
 */
interface FragmentBuffer {
    /**
     * Add a received fragment.
     *
     * @return Complete message payload if all fragments received, null otherwise
     */
    fun addFragment(
        messageId: Int,
        fragmentIndex: Int,
        fragmentTotal: Int,
        payload: ByteArray
    ): ByteArray?

    /**
     * Remove stale incomplete messages older than timeout.
     * @return Number of messages discarded
     */
    fun cleanupStale(timeoutMs: Long): Int

    /**
     * Current number of messages being reassembled.
     */
    val pendingMessageCount: Int
}

/**
 * Prevents duplicate message delivery.
 */
interface DeduplicationCache {
    /**
     * Check if packet was already seen and mark as seen.
     * @return true if this is a NEW packet, false if duplicate
     */
    fun checkAndMark(sequenceNumber: Long): Boolean

    /**
     * Clear old entries to bound memory usage.
     */
    fun compact()
}

// ============================================================================
// RTT Estimation
// ============================================================================

/**
 * Estimates RTT and calculates PTO.
 */
interface RttEstimator {
    /**
     * Update RTT estimate with a new sample.
     *
     * @param rttNanos Measured round-trip time in nanoseconds
     * @param ackDelayNanos ACK delay reported by receiver
     */
    fun update(rttNanos: Long, ackDelayNanos: Long)

    /**
     * Get current Probe Timeout value.
     * @return PTO in nanoseconds
     */
    fun getPto(): Long

    /**
     * Current smoothed RTT estimate.
     */
    val smoothedRtt: Long

    /**
     * Minimum observed RTT.
     */
    val minRtt: Long
}

// ============================================================================
// Data Classes
// ============================================================================

data class SentPacket(
    val sequenceNumber: Long,
    val messageId: Int,
    val fragmentIndex: Int,
    val fragmentTotal: Int,
    val payload: ByteArray,
    val destination: InetSocketAddress,
    val sentAt: Long = System.nanoTime(),
    val retransmitCount: Int = 0
) {
    fun retransmit(newSeqNum: Long): SentPacket = copy(
        sequenceNumber = newSeqNum,
        sentAt = System.nanoTime(),
        retransmitCount = retransmitCount + 1
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SentPacket) return false
        return sequenceNumber == other.sequenceNumber &&
                messageId == other.messageId &&
                fragmentIndex == other.fragmentIndex
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + messageId
        result = 31 * result + fragmentIndex
        return result
    }
}

data class AckFrame(
    val largestAcked: Long,
    val ackDelayMicros: Int,
    val ranges: List<AckRange>
) {
    fun contains(sequenceNumber: Long): Boolean =
        ranges.any { it.contains(sequenceNumber) }
}

data class AckRange(
    val smallest: Long,
    val largest: Long
) {
    init {
        require(smallest <= largest) { "Invalid range: smallest=$smallest > largest=$largest" }
    }

    fun contains(pn: Long): Boolean = pn in smallest..largest
}
