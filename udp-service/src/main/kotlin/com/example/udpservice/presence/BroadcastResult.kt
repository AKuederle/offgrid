package com.example.udpservice.presence

/**
 * Result of a broadcast operation.
 */
sealed class BroadcastResult {
    /** Broadcast sent successfully */
    data object Success : BroadcastResult()

    /** Broadcast rate-limited, try again later */
    data class RateLimited(val waitMillis: Long) : BroadcastResult()

    /** Broadcast failed */
    data class Failed(val reason: String) : BroadcastResult()
}
