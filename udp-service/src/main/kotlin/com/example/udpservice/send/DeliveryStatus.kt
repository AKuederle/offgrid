package com.example.udpservice.send

/**
 * Delivery status for outbound messages.
 *
 * State machine:
 * ```
 * PENDING ──────────→ SENDING
 *                         │
 *                         ├──→ DELIVERED (terminal)
 *                         │
 *                         └──→ RETRYING
 *                                 │
 *                                 ├──→ SENDING (retry)
 *                                 │
 *                                 └──→ WAITING (exhausted)
 *                                         │
 *                                         └──→ PENDING (peer activity)
 * ```
 */
enum class DeliveryStatus {
    /** Queued, not yet attempted */
    PENDING,

    /** Currently transmitting */
    SENDING,

    /** Successfully acknowledged (terminal state) */
    DELIVERED,

    /** Failed, scheduled for retry */
    RETRYING,

    /** Exhausted retries, waiting for peer activity */
    WAITING;

    /**
     * Check if transition to target status is valid.
     */
    fun canTransitionTo(target: DeliveryStatus): Boolean = when (this) {
        PENDING -> target == SENDING
        SENDING -> target in listOf(DELIVERED, RETRYING)
        RETRYING -> target in listOf(SENDING, WAITING)
        WAITING -> target == PENDING
        DELIVERED -> false // Terminal state
    }

    /**
     * Check if this is a terminal state.
     */
    val isTerminal: Boolean get() = this == DELIVERED

    /**
     * Check if this status indicates the message needs delivery.
     */
    val needsDelivery: Boolean get() = this in listOf(PENDING, RETRYING)
}
