package com.example.udpservice.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for app prefix registrations.
 *
 * Stores configuration for registered app prefixes including notification settings
 * and deep link URIs. Designed for future cross-app support via packageName field.
 *
 * @property prefix Unique prefix identifier (e.g., "broker", "alerts")
 * @property packageName Package name of registering app (for explicit broadcasts)
 * @property notificationsEnabled Whether to show notifications for this prefix
 * @property deepLinkUri URI to open when notification is tapped
 * @property createdAt Registration timestamp in milliseconds since epoch
 */
@Entity(tableName = "app_registrations")
data class AppRegistrationEntity(
    @PrimaryKey
    val prefix: String,
    val packageName: String,
    val notificationsEnabled: Boolean,
    val deepLinkUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
