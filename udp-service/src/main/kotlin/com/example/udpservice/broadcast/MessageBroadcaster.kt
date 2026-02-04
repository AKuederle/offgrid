package com.example.udpservice.broadcast

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Sends targeted broadcasts to registered client packages.
 *
 * Thread Safety: Thread-safe, can be called from any thread/coroutine.
 *
 * Usage:
 * 1. Message arrives and is persisted to database
 * 2. Look up registration for the message's prefix
 * 3. Call broadcastNewMessage with the registered package name
 */
interface MessageBroadcaster {

    /**
     * Sends an explicit broadcast to notify a package of new messages.
     *
     * The broadcast includes:
     * - Action: ACTION_NEW_MESSAGE
     * - Package: Explicit target (setPackage)
     * - Extra: prefix string
     *
     * The broadcast is minimal - just a ping. The client pulls actual
     * message data from the database.
     *
     * @param prefix The app prefix that received a message
     * @param packageName The target package to notify
     */
    fun broadcastNewMessage(prefix: String, packageName: String)
}

/**
 * Default implementation using Android's Context.sendBroadcast().
 *
 * Uses explicit component targeting to ensure only the target receiver
 * processes the notification. This allows receivers to be non-exported
 * for security while still receiving cross-package broadcasts from the broker.
 */
class MessageBroadcasterImpl(
    private val context: Context
) : MessageBroadcaster {

    override fun broadcastNewMessage(prefix: String, packageName: String) {
        val intent = Intent(BroadcastActions.ACTION_NEW_MESSAGE).apply {
            // Use explicit component: package + standard receiver class name
            setComponent(ComponentName(packageName, "$packageName.receiver.MessageReceiver"))
            putExtra(BroadcastActions.EXTRA_PREFIX, prefix)
        }
        context.sendBroadcast(intent)
    }
}
