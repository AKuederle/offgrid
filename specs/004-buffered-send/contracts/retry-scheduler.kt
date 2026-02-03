/**
 * Retry Scheduler Contract
 *
 * Feature: 004-buffered-send
 * Purpose: Defines the interface for exponential backoff retry logic
 */
package com.example.udpservice.send

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for retry behavior.
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
)

/**
 * Result of checking if a message is ready for retry.
 */
sealed class RetryDecision {
    /** Message should be retried now */
    object RetryNow : RetryDecision()

    /** Message should wait before retry */
    data class WaitUntil(val delayMillis: Long) : RetryDecision()

    /** Message has exhausted retries, move to WAITING state */
    object ExhaustedRetries : RetryDecision()
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

/**
 * Default implementation of RetryScheduler.
 */
class RetrySchedulerImpl(
    override val config: RetryConfig = RetryConfig()
) : RetryScheduler {

    override fun calculateDelay(retryCount: Int): Duration {
        val delayMillis = config.initialDelay.inWholeMilliseconds *
            Math.pow(config.multiplier, retryCount.toDouble()).toLong()
        return minOf(delayMillis, config.maxDelay.inWholeMilliseconds).let {
            Duration.parse("${it}ms")
        }
    }

    override fun shouldRetry(
        retryCount: Int,
        lastAttemptAt: Long,
        createdAt: Long,
        currentTime: Long
    ): RetryDecision {
        // Check if max retries exceeded
        if (retryCount >= config.maxRetries) {
            return RetryDecision.ExhaustedRetries
        }

        // Check if total timeout exceeded
        if (isTimeoutExceeded(createdAt, currentTime)) {
            return RetryDecision.ExhaustedRetries
        }

        // Calculate delay since last attempt
        val delay = calculateDelay(retryCount)
        val nextAttemptAt = lastAttemptAt + delay.inWholeMilliseconds

        return if (currentTime >= nextAttemptAt) {
            RetryDecision.RetryNow
        } else {
            RetryDecision.WaitUntil(nextAttemptAt - currentTime)
        }
    }

    override fun isTimeoutExceeded(createdAt: Long, currentTime: Long): Boolean {
        return (currentTime - createdAt) >= config.totalTimeout.inWholeMilliseconds
    }
}
