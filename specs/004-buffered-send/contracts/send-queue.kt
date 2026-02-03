/**
 * Send Queue Contract
 *
 * Feature: 004-buffered-send
 * Purpose: Defines the interface for managing outbound message persistence and delivery
 */
package com.example.udpservice.send

import kotlinx.coroutines.flow.Flow
import java.net.InetSocketAddress

/**
 * Delivery status for outbound messages.
 *
 * State machine:
 * PENDING → SENDING → DELIVERED (terminal)
 *                  → RETRYING → SENDING (loop)
 *                            → WAITING → PENDING (resume)
 */
enum class DeliveryStatus {
    /** Queued, not yet attempted */
    PENDING,
    /** Currently transmitting */
    SENDING,
    /** Successfully acknowledged (terminal) */
    DELIVERED,
    /** Failed, scheduled for retry */
    RETRYING,
    /** Exhausted retries, waiting for peer activity */
    WAITING
}

/**
 * Represents an outbound message queued for delivery.
 */
data class OutboundMessage(
    val id: Long,
    val peer: InetSocketAddress,
    val payload: ByteArray,
    val status: DeliveryStatus,
    val retryCount: Int,
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val deliveredAt: Long?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboundMessage) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Result of a send operation.
 */
sealed class SendResult {
    /** Message queued successfully */
    data class Queued(val messageId: Long) : SendResult()

    /** Failed to queue message */
    data class Failed(val reason: String) : SendResult()
}

/**
 * Interface for managing the outbound message queue.
 *
 * Responsibilities:
 * - Persist messages before transmission
 * - Track delivery status
 * - Support queries for pending/retrying messages
 * - Provide Flow-based observation of status changes
 */
interface SendQueue {
    /**
     * Queue a message for delivery.
     *
     * The message is persisted to storage before this method returns.
     * Actual transmission happens asynchronously.
     *
     * @param peer Target peer address
     * @param payload Message content (max 64KB)
     * @return SendResult indicating success or failure
     */
    suspend fun enqueue(peer: InetSocketAddress, payload: ByteArray): SendResult

    /**
     * Get a message by ID.
     *
     * @param messageId The message ID
     * @return The message, or null if not found
     */
    suspend fun get(messageId: Long): OutboundMessage?

    /**
     * Get all pending messages for a peer.
     *
     * Includes messages in PENDING, RETRYING, and WAITING states.
     *
     * @param peer Target peer address
     * @return List of pending messages, ordered by creation time
     */
    suspend fun getPendingForPeer(peer: InetSocketAddress): List<OutboundMessage>

    /**
     * Get all messages that need retry.
     *
     * Returns messages in RETRYING state where enough time has passed
     * based on exponential backoff.
     *
     * @return List of messages ready for retry
     */
    suspend fun getReadyForRetry(): List<OutboundMessage>

    /**
     * Update message status.
     *
     * @param messageId The message ID
     * @param status New status
     * @param retryCount Updated retry count (optional)
     */
    suspend fun updateStatus(
        messageId: Long,
        status: DeliveryStatus,
        retryCount: Int? = null
    )

    /**
     * Mark a message as delivered.
     *
     * Sets status to DELIVERED and records delivery timestamp.
     *
     * @param messageId The message ID
     */
    suspend fun markDelivered(messageId: Long)

    /**
     * Cancel a pending message.
     *
     * Removes the message from the queue. Only works for non-delivered messages.
     *
     * @param messageId The message ID
     * @return true if cancelled, false if not found or already delivered
     */
    suspend fun cancel(messageId: Long): Boolean

    /**
     * Resume all WAITING messages for a peer.
     *
     * Changes status from WAITING to PENDING for all messages
     * targeted at the specified peer.
     *
     * @param peer The peer that showed activity
     * @return Number of messages resumed
     */
    suspend fun resumeForPeer(peer: InetSocketAddress): Int

    /**
     * Observe all outbound messages.
     *
     * @return Flow emitting list of all messages on each change
     */
    fun observeAll(): Flow<List<OutboundMessage>>

    /**
     * Observe messages for a specific peer.
     *
     * @param peer Target peer address
     * @return Flow emitting list of messages for this peer
     */
    fun observeForPeer(peer: InetSocketAddress): Flow<List<OutboundMessage>>
}
