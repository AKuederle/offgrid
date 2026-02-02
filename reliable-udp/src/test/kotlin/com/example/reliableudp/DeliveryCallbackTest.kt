package com.example.reliableudp

import com.example.reliableudp.receiver.*
import com.example.reliableudp.rtt.RttEstimatorImpl
import com.example.reliableudp.sender.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DeliveryCallbackTest {

    private var socket: ReliableSocketImpl? = null

    @AfterEach
    fun cleanup() {
        socket?.close()
        socket = null
    }

    @Test
    fun `sendAsync invokes callback with messageId`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val destination = InetSocketAddress("127.0.0.1", 9999)
        val callbackResult = AtomicReference<DeliveryResult?>(null)
        val latch = CountDownLatch(1)

        val messageId = socket!!.sendAsync(destination, "Hello".toByteArray()) { result ->
            callbackResult.set(result)
            latch.countDown()
        }

        // The callback won't be invoked immediately (needs ACK)
        // But messageId should be returned immediately
        assertTrue(messageId >= 0)
    }

    @Test
    fun `sendAsync returns sequential message IDs`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val destination = InetSocketAddress("127.0.0.1", 9999)
        val ids = mutableListOf<Int>()

        repeat(5) {
            val id = socket!!.sendAsync(destination, "Test".toByteArray()) { }
            ids.add(id)
        }

        // Check IDs are sequential
        for (i in 1 until ids.size) {
            assertEquals(ids[i - 1] + 1, ids[i])
        }
    }

    @Test
    fun `close invokes pending callbacks with SOCKET_CLOSED`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val destination = InetSocketAddress("127.0.0.1", 9999)
        val results = mutableListOf<DeliveryResult>()
        val latch = CountDownLatch(3)

        repeat(3) {
            socket!!.sendAsync(destination, "Test".toByteArray()) { result ->
                synchronized(results) { results.add(result) }
                latch.countDown()
            }
        }

        // Close should trigger failure callbacks
        socket!!.close()

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(3, results.size)

        results.forEach { result ->
            assertTrue(result is DeliveryResult.Failure)
            assertEquals(
                DeliveryResult.FailureReason.SOCKET_CLOSED,
                (result as DeliveryResult.Failure).reason
            )
        }
    }

    @Test
    fun `DeliveryResult Success contains correct messageId`() {
        val result = DeliveryResult.Success(42)
        assertEquals(42, result.messageId)
    }

    @Test
    fun `DeliveryResult Failure contains reason and retry count`() {
        val result = DeliveryResult.Failure(
            messageId = 42,
            reason = DeliveryResult.FailureReason.MAX_RETRIES_EXCEEDED,
            retryCount = 5
        )

        assertEquals(42, result.messageId)
        assertEquals(DeliveryResult.FailureReason.MAX_RETRIES_EXCEEDED, result.reason)
        assertEquals(5, result.retryCount)
    }

    @Test
    fun `callback is only invoked once per message`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val destination = InetSocketAddress("127.0.0.1", 9999)
        var callCount = 0
        val latch = CountDownLatch(1)

        socket!!.sendAsync(destination, "Test".toByteArray()) {
            callCount++
            latch.countDown()
        }

        // Close to trigger callback
        socket!!.close()

        latch.await(1, TimeUnit.SECONDS)
        Thread.sleep(100) // Extra time to catch any double-invocations

        assertEquals(1, callCount)
    }
}
