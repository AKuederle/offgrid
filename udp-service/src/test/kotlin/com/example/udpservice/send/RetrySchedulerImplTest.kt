package com.example.udpservice.send

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@DisplayName("RetrySchedulerImpl")
class RetrySchedulerImplTest {

    private lateinit var scheduler: RetrySchedulerImpl

    @BeforeEach
    fun setup() {
        scheduler = RetrySchedulerImpl()
    }

    @Nested
    @DisplayName("calculateDelay")
    inner class CalculateDelay {

        @Test
        @DisplayName("returns initialDelay for first retry (retryCount=0)")
        fun `returns initialDelay for first retry`() {
            val delay = scheduler.calculateDelay(0)
            assertEquals(1.seconds, delay)
        }

        @Test
        @DisplayName("doubles delay for each retry (exponential backoff)")
        fun `doubles delay for each retry`() {
            assertEquals(1.seconds, scheduler.calculateDelay(0))
            assertEquals(2.seconds, scheduler.calculateDelay(1))
            assertEquals(4.seconds, scheduler.calculateDelay(2))
            assertEquals(8.seconds, scheduler.calculateDelay(3))
            assertEquals(16.seconds, scheduler.calculateDelay(4))
            assertEquals(32.seconds, scheduler.calculateDelay(5))
        }

        @Test
        @DisplayName("caps delay at maxDelay")
        fun `caps delay at maxDelay`() {
            // After 5 retries (32s), the next would be 64s but capped at 32s
            assertEquals(32.seconds, scheduler.calculateDelay(6))
            assertEquals(32.seconds, scheduler.calculateDelay(10))
            assertEquals(32.seconds, scheduler.calculateDelay(100))
        }

        @Test
        @DisplayName("respects custom config")
        fun `respects custom config`() {
            val customScheduler = RetrySchedulerImpl(
                RetryConfig(
                    initialDelay = 100.milliseconds,
                    maxDelay = 500.milliseconds,
                    multiplier = 2.0,
                    maxRetries = 3,
                    totalTimeout = 2.seconds
                )
            )

            assertEquals(100.milliseconds, customScheduler.calculateDelay(0))
            assertEquals(200.milliseconds, customScheduler.calculateDelay(1))
            assertEquals(400.milliseconds, customScheduler.calculateDelay(2))
            assertEquals(500.milliseconds, customScheduler.calculateDelay(3)) // Capped
        }

        @Test
        @DisplayName("handles multiplier of 3.0")
        fun `handles multiplier of 3`() {
            val customScheduler = RetrySchedulerImpl(
                RetryConfig(
                    initialDelay = 1.seconds,
                    maxDelay = 100.seconds,
                    multiplier = 3.0,
                    maxRetries = 6,
                    totalTimeout = 63.seconds
                )
            )

            assertEquals(1.seconds, customScheduler.calculateDelay(0))
            assertEquals(3.seconds, customScheduler.calculateDelay(1))
            assertEquals(9.seconds, customScheduler.calculateDelay(2))
            assertEquals(27.seconds, customScheduler.calculateDelay(3))
        }
    }

    @Nested
    @DisplayName("shouldRetry")
    inner class ShouldRetry {

        @Test
        @DisplayName("returns RetryNow when delay has elapsed")
        fun `returns RetryNow when delay has elapsed`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            // With retryCount=0, delay is 1 second (1000ms)
            // currentTime = 3001 > lastAttemptAt + 1000
            val currentTime = 3001L

            val decision = scheduler.shouldRetry(
                retryCount = 0,
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertEquals(RetryDecision.RetryNow, decision)
        }

        @Test
        @DisplayName("returns WaitUntil when delay has not elapsed")
        fun `returns WaitUntil when delay has not elapsed`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            // With retryCount=0, delay is 1 second (1000ms)
            // currentTime = 2500 < lastAttemptAt + 1000 = 3000
            val currentTime = 2500L

            val decision = scheduler.shouldRetry(
                retryCount = 0,
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertTrue(decision is RetryDecision.WaitUntil)
            assertEquals(500L, (decision as RetryDecision.WaitUntil).delayMillis)
        }

        @Test
        @DisplayName("returns ExhaustedRetries when maxRetries exceeded")
        fun `returns ExhaustedRetries when maxRetries exceeded`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            val currentTime = 100000L

            val decision = scheduler.shouldRetry(
                retryCount = 6, // Default maxRetries is 6
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertEquals(RetryDecision.ExhaustedRetries, decision)
        }

        @Test
        @DisplayName("returns ExhaustedRetries when totalTimeout exceeded")
        fun `returns ExhaustedRetries when totalTimeout exceeded`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            // Default totalTimeout is 63 seconds
            val currentTime = 1000L + 63001L // Just over 63 seconds

            val decision = scheduler.shouldRetry(
                retryCount = 2, // Still has retries left
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertEquals(RetryDecision.ExhaustedRetries, decision)
        }

        @Test
        @DisplayName("checks maxRetries before timeout")
        fun `checks maxRetries before timeout`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            val currentTime = 5000L // Well within timeout

            val decision = scheduler.shouldRetry(
                retryCount = 6, // At maxRetries
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertEquals(RetryDecision.ExhaustedRetries, decision)
        }

        @Test
        @DisplayName("returns RetryNow at exact delay boundary")
        fun `returns RetryNow at exact delay boundary`() {
            val createdAt = 1000L
            val lastAttemptAt = 2000L
            // With retryCount=0, delay is exactly 1000ms
            val currentTime = 3000L // Exactly at boundary

            val decision = scheduler.shouldRetry(
                retryCount = 0,
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertEquals(RetryDecision.RetryNow, decision)
        }

        @Test
        @DisplayName("calculates correct delay for higher retry counts")
        fun `calculates correct delay for higher retry counts`() {
            val createdAt = 1000L
            val lastAttemptAt = 10000L
            // With retryCount=3, delay is 8 seconds (8000ms)
            val currentTime = 15000L // 5 seconds after last attempt

            val decision = scheduler.shouldRetry(
                retryCount = 3,
                lastAttemptAt = lastAttemptAt,
                createdAt = createdAt,
                currentTime = currentTime
            )

            assertTrue(decision is RetryDecision.WaitUntil)
            assertEquals(3000L, (decision as RetryDecision.WaitUntil).delayMillis) // Need 3 more seconds
        }
    }

    @Nested
    @DisplayName("isTimeoutExceeded")
    inner class IsTimeoutExceeded {

        @Test
        @DisplayName("returns false when within timeout")
        fun `returns false when within timeout`() {
            val createdAt = 1000L
            val currentTime = 30000L // 29 seconds elapsed, under 63s timeout

            val exceeded = scheduler.isTimeoutExceeded(createdAt, currentTime)

            assertEquals(false, exceeded)
        }

        @Test
        @DisplayName("returns true when timeout exceeded")
        fun `returns true when timeout exceeded`() {
            val createdAt = 1000L
            val currentTime = 1000L + 63001L // Just over 63 seconds

            val exceeded = scheduler.isTimeoutExceeded(createdAt, currentTime)

            assertEquals(true, exceeded)
        }

        @Test
        @DisplayName("returns true at exact timeout boundary")
        fun `returns true at exact timeout boundary`() {
            val createdAt = 1000L
            val currentTime = 1000L + 63000L // Exactly 63 seconds

            val exceeded = scheduler.isTimeoutExceeded(createdAt, currentTime)

            assertEquals(true, exceeded)
        }

        @Test
        @DisplayName("returns false just before timeout")
        fun `returns false just before timeout`() {
            val createdAt = 1000L
            val currentTime = 1000L + 62999L // 1ms before timeout

            val exceeded = scheduler.isTimeoutExceeded(createdAt, currentTime)

            assertEquals(false, exceeded)
        }

        @Test
        @DisplayName("respects custom totalTimeout")
        fun `respects custom totalTimeout`() {
            val customScheduler = RetrySchedulerImpl(
                RetryConfig.FAST // 2 second timeout
            )

            val createdAt = 1000L

            assertEquals(false, customScheduler.isTimeoutExceeded(createdAt, 2999L)) // Under 2s
            assertEquals(true, customScheduler.isTimeoutExceeded(createdAt, 3000L)) // At 2s
            assertEquals(true, customScheduler.isTimeoutExceeded(createdAt, 5000L)) // Over 2s
        }
    }

    @Nested
    @DisplayName("config")
    inner class Config {

        @Test
        @DisplayName("exposes configuration")
        fun `exposes configuration`() {
            val config = scheduler.config

            assertEquals(1.seconds, config.initialDelay)
            assertEquals(32.seconds, config.maxDelay)
            assertEquals(2.0, config.multiplier)
            assertEquals(6, config.maxRetries)
            assertEquals(63.seconds, config.totalTimeout)
        }

        @Test
        @DisplayName("uses provided config")
        fun `uses provided config`() {
            val customConfig = RetryConfig.FAST
            val customScheduler = RetrySchedulerImpl(customConfig)

            assertEquals(customConfig, customScheduler.config)
        }
    }

    @Nested
    @DisplayName("default backoff sequence")
    inner class DefaultBackoffSequence {

        @Test
        @DisplayName("produces correct total retry time")
        fun `produces correct total retry time`() {
            // Default sequence: 1s + 2s + 4s + 8s + 16s + 32s = 63s
            var totalDelayMs = 0L
            for (i in 0 until 6) {
                totalDelayMs += scheduler.calculateDelay(i).inWholeMilliseconds
            }

            assertEquals(63_000L, totalDelayMs)
        }
    }
}
