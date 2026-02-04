package com.example.udpservice.broadcast

/**
 * Broadcast action and extras constants for message notifications.
 *
 * Used by the broker to send explicit broadcasts to client apps when new messages arrive.
 */
object BroadcastActions {
    /**
     * Action for new message arrival broadcast.
     *
     * Sent as an explicit broadcast (setPackage) to the registered package
     * when a UDP message arrives for a registered prefix.
     */
    const val ACTION_NEW_MESSAGE = "com.example.udpservice.NEW_MESSAGE"

    /**
     * Extra key for the prefix string.
     *
     * The prefix identifies which app prefix received a new message.
     * Clients use this to pull the correct messages from the database.
     */
    const val EXTRA_PREFIX = "prefix"
}
