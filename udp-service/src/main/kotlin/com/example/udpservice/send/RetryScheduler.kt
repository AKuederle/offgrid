package com.example.udpservice.send

import kotlin.time.Duration

/**
 * Result of checking if a message is ready for retry.
 */
sealed class RetryDecision {
    /** Message should be retried now */
    data object RetryNow : RetryDecision()

    /** Message should wait before retry */
    data class WaitUntil(val delayMillis: Long) : RetryDecision()

    /** Message has exhausted retries, move to WAITING state */
    data object ExhaustedRetries : RetryDecision()
}

/**
 * Interface for managing retry scheduling with exponential backoff.
 *
 * Responsibilities:
 * - Calculate next retry delay based on attempt count
 * - Determine if a message is ready for retry
 * - Track total retry time against timeout
 */
interface RetryScheduler {
    /**
     * Get the retry configuration.
     */
    val config: RetryConfig

    /**
     * Calculate the delay before the next retry attempt.
     *
     * Uses exponential backoff: delay = initialDelay * (multiplier ^ retryCount)
     * Capped at maxDelay.
     *
     * @param retryCount Number of retries already attempted (0 = first retry)
     * @return Delay duration before next attempt
     */
    fun calculateDelay(retryCount: Int): Duration

    /**
     * Determine if a message should be retried.
     *
     * @param retryCount Number of retries already attempted
     * @param lastAttemptAt Timestamp of last attempt (epoch millis)
     * @param createdAt Timestamp when message was created (epoch millis)
     * @param currentTime Current timestamp (epoch millis), defaults to now
     * @return RetryDecision indicating action to take
     */
    fun shouldRetry(
        retryCount: Int,
        lastAttemptAt: Long,
        createdAt: Long,
        currentTime: Long = System.currentTimeMillis()
    ): RetryDecision

    /**
     * Check if total retry time has been exceeded.
     *
     * @param createdAt Timestamp when message was created (epoch millis)
     * @param currentTime Current timestamp (epoch millis)
     * @return true if total timeout exceeded
     */
    fun isTimeoutExceeded(
        createdAt: Long,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean
}
