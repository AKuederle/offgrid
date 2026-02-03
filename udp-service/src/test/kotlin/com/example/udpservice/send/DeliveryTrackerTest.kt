package com.example.udpservice.send

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DeliveryTracker")
class DeliveryTrackerTest {

    private lateinit var sendQueue: SendQueue
    private lateinit var retryScheduler: RetryScheduler
    private lateinit var tracker: DeliveryTracker

    @BeforeEach
    fun setup() {
        sendQueue = mockk(relaxed = true)
        retryScheduler = mockk(relaxed = true)
        tracker = DeliveryTrackerImpl(sendQueue, retryScheduler)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("onDeliverySuccess")
    inner class OnDeliverySuccess {

        @Test
        @DisplayName("marks message as delivered")
        fun `marks message as delivered`() = runTest {
            val messageId = 1L

            tracker.onDeliverySuccess(messageId)

            coVerify { sendQueue.markDelivered(messageId) }
        }
    }

    @Nested
    @DisplayName("onDeliveryFailure")
    inner class OnDeliveryFailure {

        @Test
        @DisplayName("updates status to RETRYING with incremented retry count")
        fun `updates status to RETRYING with incremented retry count`() = runTest {
            val message = createTestMessage(
                id = 1L,
                status = DeliveryStatus.SENDING,
                retryCount = 2
            )

            coEvery { sendQueue.get(1L) } returns message
            every { retryScheduler.shouldRetry(any(), any(), any(), any()) } returns RetryDecision.RetryNow

            tracker.onDeliveryFailure(1L)

            coVerify {
                sendQueue.updateStatus(1L, DeliveryStatus.RETRYING, retryCount = 3)
            }
        }

        @Test
        @DisplayName("moves to WAITING when retries exhausted")
        fun `moves to WAITING when retries exhausted`() = runTest {
            val message = createTestMessage(
                id = 1L,
                status = DeliveryStatus.SENDING,
                retryCount = 5
            )

            coEvery { sendQueue.get(1L) } returns message
            every { retryScheduler.shouldRetry(any(), any(), any(), any()) } returns RetryDecision.ExhaustedRetries

            tracker.onDeliveryFailure(1L)

            coVerify {
                sendQueue.updateStatus(1L, DeliveryStatus.WAITING, retryCount = any())
            }
        }

        @Test
        @DisplayName("handles message not found gracefully")
        fun `handles message not found gracefully`() = runTest {
            coEvery { sendQueue.get(1L) } returns null

            // Should not throw
            tracker.onDeliveryFailure(1L)

            coVerify(exactly = 0) { sendQueue.updateStatus(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("checkRetryDecision")
    inner class CheckRetryDecision {

        @Test
        @DisplayName("returns RetryNow when scheduler says retry")
        fun `returns RetryNow when scheduler says retry`() = runTest {
            val message = createTestMessage(
                id = 1L,
                retryCount = 1,
                lastAttemptAt = 1000L,
                createdAt = 500L
            )

            every {
                retryScheduler.shouldRetry(
                    retryCount = 1,
                    lastAttemptAt = 1000L,
                    createdAt = 500L,
                    currentTime = any()
                )
            } returns RetryDecision.RetryNow

            val decision = tracker.checkRetryDecision(message)

            assertEquals(RetryDecision.RetryNow, decision)
        }

        @Test
        @DisplayName("returns WaitUntil when delay not elapsed")
        fun `returns WaitUntil when delay not elapsed`() = runTest {
            val message = createTestMessage(
                id = 1L,
                retryCount = 2,
                lastAttemptAt = System.currentTimeMillis(),
                createdAt = 1000L
            )

            every {
                retryScheduler.shouldRetry(any(), any(), any(), any())
            } returns RetryDecision.WaitUntil(5000L)

            val decision = tracker.checkRetryDecision(message)

            assertTrue(decision is RetryDecision.WaitUntil)
            assertEquals(5000L, (decision as RetryDecision.WaitUntil).delayMillis)
        }

        @Test
        @DisplayName("returns ExhaustedRetries when max attempts reached")
        fun `returns ExhaustedRetries when max attempts reached`() = runTest {
            val message = createTestMessage(
                id = 1L,
                retryCount = 6,
                lastAttemptAt = 1000L,
                createdAt = 500L
            )

            every {
                retryScheduler.shouldRetry(any(), any(), any(), any())
            } returns RetryDecision.ExhaustedRetries

            val decision = tracker.checkRetryDecision(message)

            assertEquals(RetryDecision.ExhaustedRetries, decision)
        }

        @Test
        @DisplayName("uses current time as lastAttemptAt if null")
        fun `uses current time as lastAttemptAt if null`() = runTest {
            val message = createTestMessage(
                id = 1L,
                retryCount = 0,
                lastAttemptAt = null,
                createdAt = 1000L
            )

            every {
                retryScheduler.shouldRetry(any(), any(), any(), any())
            } returns RetryDecision.RetryNow

            tracker.checkRetryDecision(message)

            verify {
                retryScheduler.shouldRetry(
                    retryCount = 0,
                    lastAttemptAt = match { it >= message.createdAt },
                    createdAt = 1000L,
                    currentTime = any()
                )
            }
        }
    }

    @Nested
    @DisplayName("prepareForSending")
    inner class PrepareForSending {

        @Test
        @DisplayName("updates status to SENDING")
        fun `updates status to SENDING`() = runTest {
            tracker.prepareForSending(1L)

            coVerify { sendQueue.updateStatus(1L, DeliveryStatus.SENDING, null) }
        }
    }

    @Nested
    @DisplayName("moveToWaiting")
    inner class MoveToWaiting {

        @Test
        @DisplayName("updates status to WAITING")
        fun `updates status to WAITING`() = runTest {
            val message = createTestMessage(id = 1L, retryCount = 5)

            tracker.moveToWaiting(message)

            coVerify { sendQueue.updateStatus(1L, DeliveryStatus.WAITING, retryCount = 5) }
        }
    }

    private fun createTestMessage(
        id: Long = 1L,
        status: DeliveryStatus = DeliveryStatus.PENDING,
        retryCount: Int = 0,
        createdAt: Long = 1000L,
        lastAttemptAt: Long? = null
    ): OutboundMessage {
        return OutboundMessage(
            id = id,
            peer = InetSocketAddress("192.168.1.1", 5000),
            payload = "Test".toByteArray(),
            status = status,
            retryCount = retryCount,
            createdAt = createdAt,
            lastAttemptAt = lastAttemptAt,
            deliveredAt = null
        )
    }
}
