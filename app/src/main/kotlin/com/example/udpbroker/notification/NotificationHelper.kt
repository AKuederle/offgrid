package com.example.udpbroker.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Builds and manages notifications for message prefixes.
 *
 * Thread Safety: All methods are thread-safe.
 *
 * Notification Lifecycle:
 * 1. BroadcastReceiver triggers WorkManager
 * 2. Worker calls show() with unread count
 * 3. User taps -> deep link opens MessagesScreen
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
 * Default implementation of NotificationHelper.
 *
 * Uses NotificationCompat for backward compatibility and
 * creates per-prefix notification channels.
 */
class NotificationHelperImpl(
    private val context: Context,
    private val channelManager: NotificationChannelManager = NotificationChannelManager(context)
) : NotificationHelper {

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    override fun show(prefix: String, count: Int, deepLinkUri: String) {
        // Ensure channel exists
        ensureChannel(prefix)

        // Create pending intent for deep link
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri)).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(prefix),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val contentText = if (count == 1) "1 new message" else "$count new messages"
        val notification = NotificationCompat.Builder(context, channelManager.getChannelId(prefix))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(prefix)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)  // Don't re-alert on update
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(getNotificationId(prefix), notification)
        } catch (e: SecurityException) {
            // Notification permission not granted - silently ignore
        }
    }

    override fun dismiss(prefix: String) {
        notificationManager.cancel(getNotificationId(prefix))
    }

    override fun ensureChannel(prefix: String) {
        channelManager.createChannel(prefix)
    }

    /**
     * Generate a stable notification ID from the prefix.
     * Uses hashCode to get a consistent integer ID.
     */
    private fun getNotificationId(prefix: String): Int {
        // Use a base offset to avoid collision with service notification (ID 1)
        return 1000 + prefix.hashCode().and(0x7FFFFFFF) % 10000
    }
}
