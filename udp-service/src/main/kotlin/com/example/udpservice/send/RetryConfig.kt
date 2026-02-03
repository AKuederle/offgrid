package com.example.udpservice.send

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for retry behavior.
 *
 * Default backoff sequence: 1s, 2s, 4s, 8s, 16s, 32s = 63 seconds total
 */
data class RetryConfig(
    /** Initial delay before first retry */
    val initialDelay: Duration = 1.seconds,

    /** Maximum delay between retries */
    val maxDelay: Duration = 32.seconds,

    /** Multiplier for exponential backoff (typically 2.0) */
    val multiplier: Double = 2.0,

    /** Maximum number of retry attempts before giving up */
    val maxRetries: Int = 6,

    /** Total timeout after which retries stop (~63 seconds with default config) */
    val totalTimeout: Duration = 63.seconds
) {
    init {
        require(initialDelay > Duration.ZERO) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(multiplier > 1.0) { "multiplier must be > 1.0" }
        require(maxRetries > 0) { "maxRetries must be positive" }
        require(totalTimeout > Duration.ZERO) { "totalTimeout must be positive" }
    }

    companion object {
        /** Default retry configuration */
        val DEFAULT = RetryConfig()

        /** Fast retry for testing */
        val FAST = RetryConfig(
            initialDelay = 100.milliseconds,
            maxDelay = 500.milliseconds,
            maxRetries = 3,
            totalTimeout = 2.seconds
        )
    }
}
