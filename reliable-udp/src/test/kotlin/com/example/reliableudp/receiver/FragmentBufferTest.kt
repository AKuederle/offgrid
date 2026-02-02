package com.example.reliableudp.receiver

import com.example.reliableudp.ReliableUdpConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class FragmentBufferTest {

    private lateinit var buffer: FragmentBufferImpl

    @BeforeEach
    fun setup() {
        buffer = FragmentBufferImpl()
    }

    @Test
    fun `initially empty`() {
        assertEquals(0, buffer.pendingMessageCount)
    }

    @Test
    fun `single fragment message returns immediately`() {
        val payload = "Hello".toByteArray()
        val result = buffer.addFragment(
            messageId = 1,
            fragmentIndex = 1,
            fragmentTotal = 1,
            payload = payload
        )

        assertNotNull(result)
        assertArrayEquals(payload, result)
        assertEquals(0, buffer.pendingMessageCount)
    }

    @Test
    fun `multi-fragment message returns when complete`() {
        val part1 = "Hello ".toByteArray()
        val part2 = "World".toByteArray()

        val result1 = buffer.addFragment(1, 1, 2, part1)
        assertNull(result1)
        assertEquals(1, buffer.pendingMessageCount)

        val result2 = buffer.addFragment(1, 2, 2, part2)
        assertNotNull(result2)
        assertEquals("Hello World", String(result2!!))
        assertEquals(0, buffer.pendingMessageCount)
    }

    @Test
    fun `fragments can arrive out of order`() {
        val part1 = "AAA".toByteArray()
        val part2 = "BBB".toByteArray()
        val part3 = "CCC".toByteArray()

        // Add in reverse order
        assertNull(buffer.addFragment(1, 3, 3, part3))
        assertNull(buffer.addFragment(1, 1, 3, part1))
        val result = buffer.addFragment(1, 2, 3, part2)

        assertNotNull(result)
        assertEquals("AAABBBCCC", String(result!!))
    }

    @Test
    fun `duplicate fragments are handled`() {
        val part1 = "AAA".toByteArray()
        val part2 = "BBB".toByteArray()

        buffer.addFragment(1, 1, 2, part1)
        buffer.addFragment(1, 1, 2, part1) // Duplicate
        buffer.addFragment(1, 1, 2, part1) // Duplicate

        val result = buffer.addFragment(1, 2, 2, part2)
        assertNotNull(result)
        assertEquals("AAABBB", String(result!!))
    }

    @Test
    fun `multiple messages can be reassembled concurrently`() {
        buffer.addFragment(1, 1, 2, "A1".toByteArray())
        buffer.addFragment(2, 1, 2, "B1".toByteArray())
        buffer.addFragment(1, 2, 2, "A2".toByteArray())
        buffer.addFragment(2, 2, 2, "B2".toByteArray())

        // Both should complete
        assertEquals(0, buffer.pendingMessageCount)
    }

    @Test
    fun `mismatched fragment total is rejected`() {
        // First fragment says total is 3
        buffer.addFragment(1, 1, 3, "A".toByteArray())

        // Second fragment says total is 2 - should be rejected
        val result = buffer.addFragment(1, 2, 2, "B".toByteArray())
        assertNull(result)

        // Message still pending with only first fragment
        assertEquals(1, buffer.pendingMessageCount)
    }

    @Test
    fun `invalid fragment index is rejected`() {
        assertNull(buffer.addFragment(1, 0, 2, "A".toByteArray()))  // Index < 1
        assertNull(buffer.addFragment(1, 3, 2, "A".toByteArray()))  // Index > total
        assertNull(buffer.addFragment(1, 1, 0, "A".toByteArray()))  // Total < 1
    }

    @Test
    fun `cleanupStale removes old incomplete messages`() {
        buffer.addFragment(1, 1, 2, "A".toByteArray())

        // Wait briefly
        Thread.sleep(50)

        // Add newer message
        buffer.addFragment(2, 1, 2, "B".toByteArray())

        assertEquals(2, buffer.pendingMessageCount)

        // Cleanup with 25ms timeout (should remove message 1)
        val removed = buffer.cleanupStale(25)

        assertEquals(1, removed)
        assertEquals(1, buffer.pendingMessageCount)
    }

    @Test
    fun `cleanupStale does not remove recent messages`() {
        buffer.addFragment(1, 1, 2, "A".toByteArray())

        val removed = buffer.cleanupStale(60_000) // 60 second timeout

        assertEquals(0, removed)
        assertEquals(1, buffer.pendingMessageCount)
    }

    @Test
    fun `cleanupStale with default timeout`() {
        buffer.addFragment(1, 1, 2, "A".toByteArray())

        // With 30 second timeout, nothing should be removed
        val removed = buffer.cleanupStale(ReliableUdpConstants.FRAGMENT_TIMEOUT_MS)
        assertEquals(0, removed)
    }

    @Test
    fun `large message reassembly preserves byte order`() {
        val size = 10_000
        val original = ByteArray(size) { (it % 256).toByte() }

        // Split into ~8 fragments
        val chunkSize = 1400
        var offset = 0
        val chunks = mutableListOf<ByteArray>()
        while (offset < size) {
            val end = minOf(offset + chunkSize, size)
            chunks.add(original.copyOfRange(offset, end))
            offset = end
        }

        // Add fragments
        var result: ByteArray? = null
        for ((index, chunk) in chunks.withIndex()) {
            result = buffer.addFragment(1, index + 1, chunks.size, chunk)
        }

        assertNotNull(result)
        assertArrayEquals(original, result)
    }

    @Test
    fun `concurrent fragment addition is thread-safe`() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    val messageId = threadId * 1000 + i
                    buffer.addFragment(messageId, 1, 2, "A".toByteArray())
                    buffer.addFragment(messageId, 2, 2, "B".toByteArray())
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All messages should be complete
        assertEquals(0, buffer.pendingMessageCount)
    }
}
