package com.example.reliableudp.sender

/**
 * Tracks sent packets awaiting acknowledgment.
 *
 * Adapted from Quincy QUIC implementation.
 *
 * Original: https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/PacketBuffer.java
 * License: Apache 2.0 (https://github.com/protocol7/quincy/blob/master/LICENSE)
 *
 * Modifications:
 * - Ported from Java to Kotlin
 * - Simplified for connectionless UDP (removed encryption levels)
 * - Added message-level operations for fragment tracking
 *
 * Thread-safe. Supports concurrent access from send and ACK processing paths.
 */
interface PacketBuffer {
    /**
     * Store a sent packet for tracking.
     *
     * @param packet The packet to track
     */
    fun put(packet: SentPacket)

    /**
     * Remove a packet when acknowledged.
     *
     * @param sequenceNumber The sequence number to remove
     * @return The packet if found, null otherwise
     */
    fun remove(sequenceNumber: Long): SentPacket?

    /**
     * Get all packets older than the given timeout.
     *
     * Does NOT remove them - caller decides whether to retransmit or fail.
     *
     * @param timeoutNanos Age threshold in nanoseconds
     * @return List of packets that have been in-flight longer than timeout
     */
    fun getOlderThan(timeoutNanos: Long): List<SentPacket>

    /**
     * Remove and return all packets for a message.
     *
     * Used when message delivery fails after max retries.
     *
     * @param messageId The message ID whose packets should be removed
     * @return List of removed packets (may be empty)
     */
    fun removeByMessageId(messageId: Int): List<SentPacket>

    /**
     * Check if any packets are pending for a message.
     *
     * @param messageId The message ID to check
     * @return true if there are unacknowledged packets for this message
     */
    fun hasPendingPackets(messageId: Int): Boolean

    /**
     * Current number of packets in buffer.
     */
    val size: Int
}
