package com.example.udpbroker

/**
 * Simple global state for tracking which prefix the UI is actively viewing.
 *
 * This is used by the BroadcastReceiver to determine whether to show
 * a notification or let the UI handle the update via Flow observation.
 *
 * When activePrefix is set, the MessagesScreen for that prefix is visible,
 * and messages will be automatically marked as read via Flow observation.
 */
object AppState {
    /**
     * The prefix that is currently being actively viewed in the UI.
     *
     * Set by MessagesScreen when it becomes visible, cleared when it exits.
     * Null when no message list is being viewed.
     *
     * Thread-safety: This is a volatile variable for simple read/write operations.
     * More complex state management would require synchronization.
     */
    @Volatile
    var activePrefix: String? = null
}
