package com.example.udpservice.send

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default implementation of [RetryScheduler] with exponential backoff.
 *
 * Calculates retry delays using the formula: delay = initialDelay * (multiplier ^ retryCount)
 * Delays are capped at maxDelay to prevent excessively long waits.
 */
class RetrySchedulerImpl(
    override val config: RetryConfig = RetryConfig()
) : RetryScheduler {

    override fun calculateDelay(retryCount: Int): Duration {
        // Calculate using Double to avoid overflow, then cap at maxDelay
        val rawDelayMs = config.initialDelay.inWholeMilliseconds.toDouble() *
            config.multiplier.pow(retryCount.toDouble())

        // Cap at maxDelay (handles both overflow and large values)
        val cappedMillis = if (rawDelayMs >= config.maxDelay.inWholeMilliseconds.toDouble()) {
            config.maxDelay.inWholeMilliseconds
        } else {
            rawDelayMs.toLong()
        }
        return cappedMillis.milliseconds
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
