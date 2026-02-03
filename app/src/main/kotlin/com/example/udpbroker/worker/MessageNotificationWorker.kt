package com.example.udpbroker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.udpbroker.notification.NotificationHelper
import com.example.udpbroker.notification.NotificationHelperImpl
import com.example.udpservice.persistence.PacketDatabase
import com.example.udpservice.registration.RegistrationRepositoryImpl

/**
 * WorkManager worker for showing message notifications in the background.
 *
 * This worker is enqueued when a new message broadcast is received while
 * the app is backgrounded. It:
 * 1. Gets the unread count from the database
 * 2. Gets the registration for the deep link URI
 * 3. Shows or updates the notification
 * 4. Dismisses the notification if unread count is 0
 */
class MessageNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MessageNotificationWorker"
        const val KEY_PREFIX = "prefix"
    }

    // Dependencies - in a real app these would be injected
    private val database by lazy { PacketDatabase.getInstance(applicationContext) }
    private val packetDao by lazy { database.packetDao() }
    private val registrationRepository by lazy {
        RegistrationRepositoryImpl(database.appRegistrationDao())
    }
    private val notificationHelper: NotificationHelper by lazy {
        NotificationHelperImpl(applicationContext)
    }

    override suspend fun doWork(): Result {
        val prefix = inputData.getString(KEY_PREFIX)
        if (prefix.isNullOrEmpty()) {
            Log.w(TAG, "Worker started without prefix")
            return Result.failure()
        }

        Log.d(TAG, "Processing notification for prefix: $prefix")

        return try {
            // Get unread count
            val unreadCount = packetDao.getUnreadCount(prefix)

            if (unreadCount == 0) {
                // No unread messages - dismiss any existing notification
                notificationHelper.dismiss(prefix)
                Log.d(TAG, "No unread messages for $prefix, dismissed notification")
                return Result.success()
            }

            // Get registration for deep link URI
            val registration = registrationRepository.getRegistration(prefix)
            if (registration == null) {
                // Prefix may have been unregistered while notification was pending
                // Message is already persisted, so this is not a failure
                Log.w(TAG, "No registration found for prefix: $prefix")
                return Result.success()
            }

            // Check if notifications are enabled
            if (!registration.notificationsEnabled) {
                Log.d(TAG, "Notifications disabled for prefix: $prefix")
                return Result.success()
            }

            // Show/update notification
            notificationHelper.show(
                prefix = prefix,
                count = unreadCount,
                deepLinkUri = registration.deepLinkUri
            )

            Log.d(TAG, "Showed notification for $prefix: $unreadCount unread")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification for $prefix", e)
            Result.failure()
        }
    }
}
