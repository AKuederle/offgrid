/**
 * Internal API Contract: RegistrationRepository
 *
 * Stores and retrieves app prefix registrations with notification configuration.
 * Backed by Room database for persistence across app restarts.
 */

package com.example.udpservice.registration

import kotlinx.coroutines.flow.Flow

/**
 * Represents a registered app prefix with its configuration.
 *
 * Designed for future AIDL cross-app support (packageName field).
 */
data class AppRegistration(
    /** Unique prefix identifier (e.g., "broker", "alerts") */
    val prefix: String,

    /** Package name of registering app (for future cross-app support) */
    val packageName: String,

    /** Whether to show notifications for this prefix */
    val notificationsEnabled: Boolean,

    /** URI to open when notification is tapped */
    val deepLinkUri: String,

    /** Registration timestamp (epoch millis) */
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Repository for app prefix registrations.
 *
 * Thread Safety: All methods are thread-safe for concurrent access.
 *
 * Persistence: Registrations survive app restarts and are stored in Room database.
 */
interface RegistrationRepository {

    /**
     * Registers a new prefix or updates existing registration.
     *
     * @param registration The registration configuration
     * @return true if created new, false if updated existing
     */
    suspend fun register(registration: AppRegistration): Boolean

    /**
     * Removes a prefix registration.
     *
     * @param prefix The prefix to unregister
     * @return true if registration existed and was removed
     */
    suspend fun unregister(prefix: String): Boolean

    /**
     * Gets registration for a specific prefix.
     *
     * @param prefix The prefix to look up
     * @return Registration or null if not registered
     */
    suspend fun getRegistration(prefix: String): AppRegistration?

    /**
     * Observes all registrations with notifications enabled.
     *
     * Updates whenever registrations change.
     */
    fun observeEnabledRegistrations(): Flow<List<AppRegistration>>

    /**
     * Observes all registrations regardless of notification setting.
     */
    fun observeAllRegistrations(): Flow<List<AppRegistration>>

    /**
     * Checks if a prefix is registered.
     *
     * @param prefix The prefix to check
     * @return true if registered
     */
    suspend fun isRegistered(prefix: String): Boolean

    /**
     * Gets all registered prefixes.
     *
     * @return Set of registered prefix strings
     */
    suspend fun getRegisteredPrefixes(): Set<String>
}
