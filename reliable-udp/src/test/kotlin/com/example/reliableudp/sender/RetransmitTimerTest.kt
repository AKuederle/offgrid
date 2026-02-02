package com.example.reliableudp.sender

import com.example.reliableudp.ReliableUdpConstants
import com.example.reliableudp.rtt.RttEstimatorImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RetransmitTimerTest {

    private lateinit var packetBuffer: PacketBufferImpl
    private lateinit var rttEstimator: RttEstimatorImpl
    private lateinit var timer: RetransmitTimerImpl
    private lateinit var scope: CoroutineScope
    private val testDestination = InetSocketAddress("127.0.0.1", 5000)

    @BeforeEach
    fun setup() {
        packetBuffer = PacketBufferImpl()
        rttEstimator = RttEstimatorImpl()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        timer = RetransmitTimerImpl(
            packetBuffer = packetBuffer,
            rttEstimator = rttEstimator,
            checkIntervalMs = 5L, // Fast checking for tests
            scope = scope
        )
    }

    @AfterEach
    fun teardown() {
        timer.stop()
        scope.cancel()
    }

    private fun createPacket(
        seqNum: Long,
        retransmitCount: Int = 0,
        sentAt: Long = System.nanoTime()
    ) = SentPacket(
        sequenceNumber = seqNum,
        messageId = 1,
        fragmentIndex = 1,
        fragmentTotal = 1,
        payload = ByteArray(100),
        destination = testDestination,
        sentAt = sentAt,
        retransmitCount = retransmitCount
    )

    @Test
    fun `onRetransmit is called for old packets`() = runTest {
        // Use very short RTT so PTO is small
        rttEstimator.update(1_000_000L, 0) // 1ms RTT → ~5ms PTO

        val retransmitted = CopyOnWriteArrayList<SentPacket>()
        val latch = CountDownLatch(1)

        timer.onRetransmit = { packet ->
            retransmitted.add(packet)
            latch.countDown()
        }

        // Add old packet (sent 100ms ago)
        val oldPacket = createPacket(1, sentAt = System.nanoTime() - 100_000_000L)
        packetBuffer.put(oldPacket)

        timer.start()

        // Wait for callback
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Retransmit callback not called")
        assertEquals(1, retransmitted.size)
        assertEquals(1L, retransmitted[0].sequenceNumber)
    }

    @Test
    fun `onMaxRetriesExceeded is called after max retries`() = runTest {
        rttEstimator.update(1_000_000L, 0) // 1ms RTT

        val failed = CopyOnWriteArrayList<SentPacket>()
        val latch = CountDownLatch(1)

        timer.onMaxRetriesExceeded = { packet ->
            failed.add(packet)
            latch.countDown()
        }

        // Add packet that has already been retried MAX_RETRIES times
        val exhaustedPacket = createPacket(
            seqNum = 1,
            retransmitCount = ReliableUdpConstants.MAX_RETRIES,
            sentAt = System.nanoTime() - 100_000_000L
        )
        packetBuffer.put(exhaustedPacket)

        timer.start()

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Max retries callback not called")
        assertEquals(1, failed.size)
        assertEquals(1L, failed[0].sequenceNumber)
    }

    @Test
    fun `packet is removed from buffer on max retries`() = runTest {
        rttEstimator.update(1_000_000L, 0)

        val latch = CountDownLatch(1)
        timer.onMaxRetriesExceeded = { latch.countDown() }

        val exhaustedPacket = createPacket(
            seqNum = 1,
            retransmitCount = ReliableUdpConstants.MAX_RETRIES,
            sentAt = System.nanoTime() - 100_000_000L
        )
        packetBuffer.put(exhaustedPacket)

        timer.start()
        latch.await(500, TimeUnit.MILLISECONDS)

        // Packet should be removed
        assertEquals(0, packetBuffer.size)
    }

    @Test
    fun `stop cancels the timer loop`() {
        val retransmitted = CopyOnWriteArrayList<SentPacket>()
        timer.onRetransmit = { retransmitted.add(it) }

        timer.start()
        timer.stop()

        // Add old packet after stopping
        rttEstimator.update(1_000_000L, 0)
        val oldPacket = createPacket(1, sentAt = System.nanoTime() - 100_000_000L)
        packetBuffer.put(oldPacket)

        Thread.sleep(100)

        // Should not have been retransmitted
        assertEquals(0, retransmitted.size)
    }

    @Test
    fun `start is idempotent`() {
        var callCount = 0
        timer.onRetransmit = { callCount++ }

        timer.start()
        timer.start() // Second start should be ignored
        timer.start() // Third start should be ignored

        Thread.sleep(50)
        timer.stop()

        // Should only have one timer running
        // (difficult to test directly, but no exceptions should occur)
    }

    @Test
    fun `callbacks are optional`() {
        // No callbacks set
        rttEstimator.update(1_000_000L, 0)
        val oldPacket = createPacket(1, sentAt = System.nanoTime() - 100_000_000L)
        packetBuffer.put(oldPacket)

        timer.start()
        Thread.sleep(50)
        timer.stop()

        // Should not throw any exceptions
    }
}
