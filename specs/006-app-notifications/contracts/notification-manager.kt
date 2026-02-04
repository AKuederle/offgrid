/**
 * Internal API Contract: NotificationHelper
 *
 * Client-side notification handling. Lives in app module.
 * Builds and shows notifications for message prefixes.
 *
 * Architecture: Client apps handle their own notifications
 * - Broker broadcasts minimal ping
 * - Client pulls messages from broker's database
 * - Client shows notification with proper context (decryption, formatting)
 */

package com.example.udpbroker.notification

import android.content.Context

/**
 * Builds and manages notifications for message prefixes.
 *
 * Thread Safety: All methods are thread-safe.
 *
 * Notification Lifecycle:
 * 1. BroadcastReceiver triggers WorkManager
 * 2. Worker calls show() with unread count
 * 3. User taps → deep link opens MessagesScreen
 * 4. MessagesScreen marks messages as read
 * 5. If unread == 0, worker calls dismiss()
 */
interface NotificationHelper {

    /**
     * Shows or updates a notification for a prefix.
     *
     * Uses per-prefix notification channel.
     * Notification ID is stable per prefix (allows update).
     *
     * @param prefix The app prefix
     * @param count Number of unread messages
     * @param deepLinkUri URI to open when notification tapped
     */
    fun show(prefix: String, count: Int, deepLinkUri: String)

    /**
     * Dismisses the notification for a prefix.
     *
     * Called when unread count reaches 0.
     *
     * @param prefix The app prefix
     */
    fun dismiss(prefix: String)

    /**
     * Creates notification channel for a prefix if not exists.
     *
     * Safe to call multiple times - no-op if channel exists.
     *
     * @param prefix The app prefix
     */
    fun ensureChannel(prefix: String)
}

/**
 * Manages per-prefix notification channels.
 */
interface NotificationChannelManager {

    /**
     * Creates notification channel for a prefix.
     *
     * Channel ID format: "messages_{prefix}"
     * Channel name: "Messages: {prefix}"
     *
     * @param prefix The app prefix
     */
    fun createChannel(prefix: String)

    /**
     * Gets channel ID for a prefix.
     */
    fun getChannelId(prefix: String): String = "messages_$prefix"
}
