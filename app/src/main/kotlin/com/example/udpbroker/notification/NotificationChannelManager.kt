package com.example.udpbroker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Manages per-prefix notification channels.
 *
 * Creates notification channels dynamically for each app prefix,
 * allowing users to control notifications per-prefix in system settings.
 */
class NotificationChannelManager(
    private val context: Context
) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Creates notification channel for a prefix if it doesn't exist.
     *
     * Channel ID format: "messages_{prefix}"
     * Channel name: "Messages: {prefix}"
     *
     * Safe to call multiple times - no-op if channel exists.
     *
     * @param prefix The app prefix
     */
    fun createChannel(prefix: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelId(prefix)

            // Check if channel already exists
            if (notificationManager.getNotificationChannel(channelId) != null) {
                return
            }

            val channel = NotificationChannel(
                channelId,
                "Messages: $prefix",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for $prefix messages"
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Gets channel ID for a prefix.
     *
     * @param prefix The app prefix
     * @return The notification channel ID
     */
    fun getChannelId(prefix: String): String = "messages_$prefix"
}
