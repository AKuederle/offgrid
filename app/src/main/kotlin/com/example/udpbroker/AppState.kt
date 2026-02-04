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
     * Thread-safety: @Volatile ensures visibility but not atomicity. There's a
     * potential check-then-act race where a notification could be shown/suppressed
     * incorrectly if the user navigates at the exact moment a message arrives.
     * This is acceptable as an extra or missed notification has minimal impact.
     */
    @Volatile
    var activePrefix: String? = null
}
