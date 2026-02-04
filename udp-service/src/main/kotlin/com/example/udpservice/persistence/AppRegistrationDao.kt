package com.example.udpservice.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for app registration operations.
 *
 * Provides suspend functions for writes and Flow for reactive reads.
 * All database operations are thread-safe via Room's internal concurrency handling.
 */
@Dao
interface AppRegistrationDao {

    /**
     * Insert or update a registration.
     * Uses REPLACE strategy to update existing registrations.
     *
     * @param registration The registration entity to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistration(registration: AppRegistrationEntity)

    /**
     * Get a registration by prefix.
     *
     * @param prefix The prefix to look up
     * @return The registration entity or null if not found
     */
    @Query("SELECT * FROM app_registrations WHERE prefix = :prefix")
    suspend fun getRegistration(prefix: String): AppRegistrationEntity?

    /**
     * Delete a registration by prefix.
     *
     * @param prefix The prefix to unregister
     * @return Number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM app_registrations WHERE prefix = :prefix")
    suspend fun deleteRegistration(prefix: String): Int

    /**
     * Observe all registrations with notifications enabled.
     * Flow emits a new list whenever registrations change.
     *
     * @return Flow emitting list of enabled registrations
     */
    @Query("SELECT * FROM app_registrations WHERE notificationsEnabled = 1")
    fun observeEnabledRegistrations(): Flow<List<AppRegistrationEntity>>

    /**
     * Observe all registrations regardless of notification setting.
     * Flow emits a new list whenever registrations change.
     *
     * @return Flow emitting list of all registrations
     */
    @Query("SELECT * FROM app_registrations")
    fun observeAllRegistrations(): Flow<List<AppRegistrationEntity>>

    /**
     * Get all registered prefixes.
     *
     * @return List of all registered prefix strings
     */
    @Query("SELECT prefix FROM app_registrations")
    suspend fun getAllPrefixes(): List<String>

    /**
     * Check if a prefix is registered.
     *
     * @param prefix The prefix to check
     * @return true if the prefix exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM app_registrations WHERE prefix = :prefix)")
    suspend fun isRegistered(prefix: String): Boolean
}
