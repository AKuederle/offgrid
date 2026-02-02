package com.example.reliableudp.receiver

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class AckQueueTest {

    private lateinit var queue: AckQueueImpl

    @BeforeEach
    fun setup() {
        queue = AckQueueImpl()
    }

    @Test
    fun `initially empty`() {
        assertEquals(0, queue.pendingCount)
    }

    @Test
    fun `enqueue increases pending count`() = runTest {
        queue.enqueue(1)
        assertEquals(1, queue.pendingCount)

        queue.enqueue(2)
        assertEquals(2, queue.pendingCount)
    }

    @Test
    fun `drain returns null when empty`() = runTest {
        assertNull(queue.drain())
    }

    @Test
    fun `drain returns AckFrame with single packet`() = runTest {
        queue.enqueue(42)

        val frame = queue.drain()
        assertNotNull(frame)
        assertEquals(42, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size)
        assertTrue(frame.contains(42))
    }

    @Test
    fun `drain clears pending packets`() = runTest {
        queue.enqueue(1)
        queue.enqueue(2)
        queue.drain()

        assertEquals(0, queue.pendingCount)
        assertNull(queue.drain())
    }

    @Test
    fun `drain coalesces contiguous packets into single range`() = runTest {
        queue.enqueue(1)
        queue.enqueue(2)
        queue.enqueue(3)
        queue.enqueue(4)
        queue.enqueue(5)

        val frame = queue.drain()
        assertNotNull(frame)
        assertEquals(5, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size)

        // All packets should be acknowledged
        (1L..5L).forEach { assertTrue(frame.contains(it)) }
    }

    @Test
    fun `drain creates multiple ranges for gaps`() = runTest {
        // Packets: 1, 2, 3, 7, 8, 9
        queue.enqueue(1)
        queue.enqueue(2)
        queue.enqueue(3)
        queue.enqueue(7)
        queue.enqueue(8)
        queue.enqueue(9)

        val frame = queue.drain()
        assertNotNull(frame)
        assertEquals(9, frame!!.largestAcked)
        assertEquals(2, frame.ranges.size)

        // Check acknowledged packets
        assertTrue(frame.contains(1))
        assertTrue(frame.contains(2))
        assertTrue(frame.contains(3))
        assertFalse(frame.contains(4))
        assertFalse(frame.contains(5))
        assertFalse(frame.contains(6))
        assertTrue(frame.contains(7))
        assertTrue(frame.contains(8))
        assertTrue(frame.contains(9))
    }

    @Test
    fun `enqueue handles out-of-order packets`() = runTest {
        queue.enqueue(5)
        queue.enqueue(3)
        queue.enqueue(1)
        queue.enqueue(4)
        queue.enqueue(2)

        val frame = queue.drain()
        assertNotNull(frame)
        assertEquals(5, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size) // Should coalesce to single range
    }

    @Test
    fun `duplicate packets are deduplicated`() = runTest {
        queue.enqueue(1)
        queue.enqueue(1)
        queue.enqueue(1)

        val frame = queue.drain()
        assertNotNull(frame)
        assertEquals(1, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size)
        assertEquals(1, frame.ranges[0].size)
    }

    @Test
    fun `ackDelay is calculated`() = runTest {
        queue.enqueue(1)
        Thread.sleep(10) // Wait 10ms

        val frame = queue.drain()
        assertNotNull(frame)
        // ackDelay should be approximately 10000 microseconds (10ms)
        assertTrue(frame!!.ackDelayMicros >= 5000, "ackDelay should be at least 5000us")
    }
}
