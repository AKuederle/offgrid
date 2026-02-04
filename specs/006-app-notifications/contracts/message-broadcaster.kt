/**
 * Internal API Contract: MessageBroadcaster
 *
 * Sends explicit broadcasts to registered client packages when messages arrive.
 * Lives in udp-service module.
 */

package com.example.udpservice.broadcast

import android.content.Context

/**
 * Broadcast action and extras constants.
 */
object BroadcastActions {
    /** Action for new message arrival */
    const val ACTION_NEW_MESSAGE = "com.example.udpservice.NEW_MESSAGE"

    /** Extra: prefix string */
    const val EXTRA_PREFIX = "prefix"
}

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
 */
class MessageBroadcasterImpl(
    private val context: Context
) : MessageBroadcaster {

    override fun broadcastNewMessage(prefix: String, packageName: String) {
        val intent = android.content.Intent(BroadcastActions.ACTION_NEW_MESSAGE).apply {
            setPackage(packageName)
            putExtra(BroadcastActions.EXTRA_PREFIX, prefix)
        }
        context.sendBroadcast(intent)
    }
}
