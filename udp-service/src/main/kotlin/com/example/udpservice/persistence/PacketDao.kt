package com.example.udpservice.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for packet persistence operations.
 *
 * Provides suspend functions for writes (non-blocking) and Flow for reactive reads.
 * All database operations are thread-safe via Room's internal concurrency handling.
 */
@Dao
interface PacketDao {

    /**
     * Insert a single packet.
     * @param packet The packet entity to insert
     * @return The auto-generated row ID
     */
    @Insert
    suspend fun insertPacket(packet: PacketEntity): Long

    /**
     * Batch insert multiple packets.
     * Wrapped in a transaction automatically by Room.
     * @param packets List of packet entities to insert
     */
    @Insert
    suspend fun insertPackets(packets: List<PacketEntity>)

    /**
     * Observe packets as a reactive Flow, ordered newest first.
     * Flow emits a new list whenever the database changes.
     * @param limit Maximum number of packets to return
     * @return Flow emitting list of packets
     */
    @Query("SELECT * FROM packets ORDER BY timestamp DESC LIMIT :limit")
    fun observePackets(limit: Int = 100): Flow<List<PacketEntity>>

    /**
     * Observe packets for a specific appId, ordered newest first.
     * Flow emits a new list whenever the database changes.
     * @param appId The application identifier to filter by
     * @param limit Maximum number of packets to return
     * @return Flow emitting list of packets for the given appId
     */
    @Query("SELECT * FROM packets WHERE appId = :appId ORDER BY timestamp DESC LIMIT :limit")
    fun observePacketsByAppId(appId: String, limit: Int = 100): Flow<List<PacketEntity>>

    /**
     * Observe total packet count as a reactive Flow.
     * @return Flow emitting the current count
     */
    @Query("SELECT COUNT(*) FROM packets")
    fun observePacketCount(): Flow<Int>

    /**
     * Observe packet count for a specific appId.
     * @param appId The application identifier to filter by
     * @return Flow emitting the count for the given appId
     */
    @Query("SELECT COUNT(*) FROM packets WHERE appId = :appId")
    fun observePacketCountByAppId(appId: String): Flow<Int>

    /**
     * Get a single packet by ID.
     * @param id The packet ID
     * @return The packet entity or null if not found
     */
    @Query("SELECT * FROM packets WHERE id = :id")
    suspend fun getPacketById(id: Long): PacketEntity?

    /**
     * Delete all packets from the database.
     * @return Number of rows deleted
     */
    @Query("DELETE FROM packets")
    suspend fun deleteAllPackets(): Int

    /**
     * Delete all packets for a specific appId.
     * @param appId The application identifier to delete packets for
     * @return Number of rows deleted
     */
    @Query("DELETE FROM packets WHERE appId = :appId")
    suspend fun deletePacketsByAppId(appId: String): Int
}
