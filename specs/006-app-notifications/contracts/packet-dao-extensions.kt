/**
 * Internal API Contract: PacketDao Extensions
 *
 * New DAO methods for unread message tracking.
 * These extend the existing PacketDao interface.
 */

package com.example.udpservice.persistence

import kotlinx.coroutines.flow.Flow

/**
 * Extended PacketDao methods for unread tracking.
 *
 * Add these to existing PacketDao interface.
 */
interface PacketDaoUnreadExtensions {

    /**
     * Observes the count of unread packets for a specific app prefix.
     *
     * Updates reactively when packets are added or marked as read.
     *
     * @param appId The app prefix to count unread for
     * @return Flow emitting current unread count
     *
     * SQL: SELECT COUNT(*) FROM packets WHERE appId = :appId AND isRead = 0
     */
    fun observeUnreadCount(appId: String): Flow<Int>

    /**
     * Gets the current unread count for a prefix (one-shot).
     *
     * @param appId The app prefix to count
     * @return Current unread count
     *
     * SQL: SELECT COUNT(*) FROM packets WHERE appId = :appId AND isRead = 0
     */
    suspend fun getUnreadCount(appId: String): Int

    /**
     * Marks all unread packets for a prefix as read.
     *
     * @param appId The app prefix
     * @return Number of packets marked as read
     *
     * SQL: UPDATE packets SET isRead = 1 WHERE appId = :appId AND isRead = 0
     */
    suspend fun markAllAsRead(appId: String): Int

    /**
     * Marks specific packets as read by their IDs.
     *
     * @param ids List of packet IDs to mark as read
     * @return Number of packets marked as read
     *
     * SQL: UPDATE packets SET isRead = 1 WHERE id IN (:ids)
     */
    suspend fun markAsRead(ids: List<Long>): Int

    /**
     * Observes unread packets for a prefix (for notification content).
     *
     * @param appId The app prefix
     * @param limit Maximum packets to observe
     * @return Flow of unread packets
     *
     * SQL: SELECT * FROM packets WHERE appId = :appId AND isRead = 0
     *      ORDER BY timestamp DESC LIMIT :limit
     */
    fun observeUnreadPackets(appId: String, limit: Int = 100): Flow<List<PacketEntity>>
}
