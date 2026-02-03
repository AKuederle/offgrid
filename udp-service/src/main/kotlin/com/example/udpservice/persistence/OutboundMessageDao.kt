package com.example.udpservice.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.udpservice.send.DeliveryStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for outbound message persistence operations.
 *
 * Provides suspend functions for writes and Flow for reactive reads.
 */
@Dao
interface OutboundMessageDao {

    /**
     * Insert a new outbound message.
     * @param message The message entity to insert
     * @return The auto-generated row ID
     */
    @Insert
    suspend fun insert(message: OutboundMessageEntity): Long

    /**
     * Update an existing message.
     * @param message The message entity to update
     */
    @Update
    suspend fun update(message: OutboundMessageEntity)

    /**
     * Get a message by ID.
     * @param id The message ID
     * @return The message entity or null if not found
     */
    @Query("SELECT * FROM outbound_messages WHERE id = :id")
    suspend fun getById(id: Long): OutboundMessageEntity?

    /**
     * Observe all outbound messages, ordered by creation time.
     * @return Flow emitting list of all messages
     */
    @Query("SELECT * FROM outbound_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboundMessageEntity>>

    /**
     * Observe messages for a specific peer.
     * @param host Peer host address
     * @param port Peer port
     * @return Flow emitting list of messages for this peer
     */
    @Query("SELECT * FROM outbound_messages WHERE peerHost = :host AND peerPort = :port ORDER BY createdAt ASC")
    fun observeForPeer(host: String, port: Int): Flow<List<OutboundMessageEntity>>

    /**
     * Get all pending messages (need delivery).
     * Returns messages in PENDING or RETRYING status.
     * @return List of messages that need delivery
     */
    @Query("SELECT * FROM outbound_messages WHERE status IN ('PENDING', 'RETRYING') ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<OutboundMessageEntity>

    /**
     * Get pending messages for a specific peer.
     * @param host Peer host address
     * @param port Peer port
     * @return List of pending messages for this peer
     */
    @Query("SELECT * FROM outbound_messages WHERE peerHost = :host AND peerPort = :port AND status IN ('PENDING', 'RETRYING', 'WAITING') ORDER BY createdAt ASC")
    suspend fun getPendingForPeer(host: String, port: Int): List<OutboundMessageEntity>

    /**
     * Get messages in WAITING status for a specific peer.
     * @param host Peer host address
     * @param port Peer port
     * @return List of waiting messages for this peer
     */
    @Query("SELECT * FROM outbound_messages WHERE peerHost = :host AND peerPort = :port AND status = 'WAITING' ORDER BY createdAt ASC")
    suspend fun getWaitingForPeer(host: String, port: Int): List<OutboundMessageEntity>

    /**
     * Get messages ready for retry based on backoff timing.
     * @param beforeTime Only return messages with lastAttemptAt before this time
     * @return List of messages ready for retry
     */
    @Query("SELECT * FROM outbound_messages WHERE status = 'RETRYING' AND (lastAttemptAt IS NULL OR lastAttemptAt < :beforeTime) ORDER BY createdAt ASC")
    suspend fun getReadyForRetry(beforeTime: Long): List<OutboundMessageEntity>

    /**
     * Update message status.
     * @param id Message ID
     * @param status New status
     */
    @Query("UPDATE outbound_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DeliveryStatus)

    /**
     * Update message status and retry count.
     * @param id Message ID
     * @param status New status
     * @param retryCount New retry count
     * @param lastAttemptAt Timestamp of this attempt
     */
    @Query("UPDATE outbound_messages SET status = :status, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt WHERE id = :id")
    suspend fun updateStatusWithRetry(id: Long, status: DeliveryStatus, retryCount: Int, lastAttemptAt: Long)

    /**
     * Mark a message as delivered.
     * @param id Message ID
     * @param deliveredAt Delivery timestamp
     */
    @Query("UPDATE outbound_messages SET status = 'DELIVERED', deliveredAt = :deliveredAt WHERE id = :id")
    suspend fun markDelivered(id: Long, deliveredAt: Long)

    /**
     * Resume all WAITING messages for a peer by changing status to PENDING.
     * @param host Peer host address
     * @param port Peer port
     * @return Number of messages resumed
     */
    @Query("UPDATE outbound_messages SET status = 'PENDING', retryCount = 0 WHERE peerHost = :host AND peerPort = :port AND status = 'WAITING'")
    suspend fun resumeForPeer(host: String, port: Int): Int

    /**
     * Delete a message by ID.
     * @param id Message ID
     * @return Number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM outbound_messages WHERE id = :id")
    suspend fun delete(id: Long): Int

    /**
     * Delete all delivered messages older than a given time.
     * For cleanup of old delivered messages.
     * @param beforeTime Delete messages delivered before this time
     * @return Number of rows deleted
     */
    @Query("DELETE FROM outbound_messages WHERE status = 'DELIVERED' AND deliveredAt < :beforeTime")
    suspend fun deleteDeliveredBefore(beforeTime: Long): Int

    /**
     * Count messages by status.
     * @param status The status to count
     * @return Count of messages with this status
     */
    @Query("SELECT COUNT(*) FROM outbound_messages WHERE status = :status")
    suspend fun countByStatus(status: DeliveryStatus): Int
}
