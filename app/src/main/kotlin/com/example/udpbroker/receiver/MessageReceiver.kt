package com.example.udpbroker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.udpbroker.AppState
import com.example.udpbroker.worker.MessageNotificationWorker
import com.example.udpservice.broadcast.BroadcastActions

/**
 * BroadcastReceiver for handling new message notifications from the UDP service.
 *
 * When the broker service persists a message, it sends an explicit broadcast
 * to this receiver. The receiver then:
 * - If the UI is actively showing this prefix: does nothing (UI updates via Flow)
 * - If backgrounded: enqueues a WorkManager job to show a notification
 */
class MessageReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MessageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BroadcastActions.ACTION_NEW_MESSAGE) {
            return
        }

        val prefix = intent.getStringExtra(BroadcastActions.EXTRA_PREFIX)
        if (prefix.isNullOrEmpty()) {
            Log.w(TAG, "Received broadcast without prefix extra")
            return
        }

        Log.d(TAG, "Received new message notification for prefix: $prefix, activePrefix: ${AppState.activePrefix}")

        // For test app: always show notifications to make testing easier
        // Production apps would check: if (AppState.activePrefix == prefix) return

        // Enqueue background work to show notification
        enqueueNotificationWork(context, prefix)
    }

    private fun enqueueNotificationWork(context: Context, prefix: String) {
        val workRequest = OneTimeWorkRequestBuilder<MessageNotificationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(MessageNotificationWorker.KEY_PREFIX to prefix))
            .build()

        // Use unique work with KEEP policy to avoid cancelling in-progress work
        // If a notification job is already running/pending for this prefix, let it complete
        WorkManager.getInstance(context).enqueueUniqueWork(
            "notification_$prefix",
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "Enqueued notification work for prefix: $prefix")
    }
}
