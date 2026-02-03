package com.example.udpservice.send

import kotlinx.coroutines.flow.Flow
import java.net.InetSocketAddress

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

    /**
     * Start the send queue processing.
     *
     * Begins monitoring for messages to send and handling retries.
     */
    suspend fun start()

    /**
     * Stop the send queue processing.
     */
    fun stop()

    /**
     * Notify of peer activity (received a message from peer).
     *
     * This triggers resumption of any WAITING messages for that peer.
     *
     * @param peer The peer that showed activity
     */
    suspend fun onPeerActivity(peer: InetSocketAddress)
}
