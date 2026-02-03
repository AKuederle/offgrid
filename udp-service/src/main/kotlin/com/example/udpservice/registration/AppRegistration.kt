package com.example.udpservice.registration

/**
 * Represents a registered app prefix with its configuration.
 *
 * This is the domain model for app registrations, independent of the Room entity.
 * Designed for future AIDL cross-app support via the packageName field.
 *
 * @property prefix Unique prefix identifier (e.g., "broker", "alerts")
 * @property packageName Package name of registering app (for explicit broadcasts)
 * @property notificationsEnabled Whether to show notifications for this prefix
 * @property deepLinkUri URI to open when notification is tapped
 * @property createdAt Registration timestamp in milliseconds since epoch
 */
data class AppRegistration(
    val prefix: String,
    val packageName: String,
    val notificationsEnabled: Boolean,
    val deepLinkUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
