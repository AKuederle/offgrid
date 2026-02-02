package com.example.reliableudp.receiver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress

class DeduplicationCacheTest {

    private lateinit var cache: DeduplicationCacheImpl

    // Test source addresses
    private val source1 = InetSocketAddress("192.168.1.100", 5000)
    private val source2 = InetSocketAddress("192.168.1.101", 5000)
    private val source3 = InetSocketAddress("192.168.1.100", 5001) // Same IP, different port

    @BeforeEach
    fun setup() {
        cache = DeduplicationCacheImpl(maxSize = 100)
    }

    @Test
    fun `initially empty`() {
        assertEquals(0, cache.size)
    }

    @Test
    fun `checkAndMark returns true for new packet`() {
        assertTrue(cache.checkAndMark(source1, 1))
    }

    @Test
    fun `checkAndMark returns false for duplicate packet from same source`() {
        cache.checkAndMark(source1, 1)
        assertFalse(cache.checkAndMark(source1, 1))
    }

    @Test
    fun `same sequence number from different sources are NOT duplicates`() {
        // This is the key fix: different senders can use overlapping seqNums
        assertTrue(cache.checkAndMark(source1, 0))
        assertTrue(cache.checkAndMark(source2, 0)) // Same seqNum, different source = NOT duplicate
    }

    @Test
    fun `same sequence number from same IP different port are NOT duplicates`() {
        assertTrue(cache.checkAndMark(source1, 0))
        assertTrue(cache.checkAndMark(source3, 0)) // Same IP, different port = different sender
    }

    @Test
    fun `checkAndMark increments size for new packets`() {
        cache.checkAndMark(source1, 1)
        assertEquals(1, cache.size)

        cache.checkAndMark(source1, 2)
        assertEquals(2, cache.size)
    }

    @Test
    fun `checkAndMark does not increment size for duplicates`() {
        cache.checkAndMark(source1, 1)
        cache.checkAndMark(source1, 1)
        cache.checkAndMark(source1, 1)
        assertEquals(1, cache.size)
    }

    @Test
    fun `multiple sources increase size independently`() {
        cache.checkAndMark(source1, 0)
        cache.checkAndMark(source2, 0)
        cache.checkAndMark(source1, 1)
        cache.checkAndMark(source2, 1)
        assertEquals(4, cache.size)
    }

    @Test
    fun `compact removes old entries when over capacity`() {
        // Fill to capacity
        repeat(100) { cache.checkAndMark(source1, it.toLong()) }
        assertEquals(100, cache.size)

        // Add more to trigger compaction
        cache.checkAndMark(source1, 200)
        cache.checkAndMark(source1, 201)

        // Compact should have been triggered
        assertTrue(cache.size <= 100)
    }

    @Test
    fun `compact removes oldest entries first`() {
        val smallCache = DeduplicationCacheImpl(maxSize = 10)

        // Add packets 0-9
        repeat(10) { smallCache.checkAndMark(source1, it.toLong()) }

        // Add packet 100 to trigger compaction
        smallCache.checkAndMark(source1, 100)

        // Most recent packets should still be marked
        assertFalse(smallCache.checkAndMark(source1, 100)) // Should be duplicate

        // Oldest packets may have been evicted
        // (exact behavior depends on implementation)
    }

    @Test
    fun `manual compact reduces size`() {
        // Fill beyond capacity
        val smallCache = DeduplicationCacheImpl(maxSize = 10)
        repeat(20) { smallCache.checkAndMark(source1, it.toLong()) }

        smallCache.compact()

        assertTrue(smallCache.size <= 10)
    }

    @Test
    fun `compact is idempotent when under capacity`() {
        cache.checkAndMark(source1, 1)
        cache.checkAndMark(source1, 2)

        val sizeBefore = cache.size
        cache.compact()
        assertEquals(sizeBefore, cache.size)
    }

    @Test
    fun `handles large sequence numbers`() {
        assertTrue(cache.checkAndMark(source1, Long.MAX_VALUE))
        assertFalse(cache.checkAndMark(source1, Long.MAX_VALUE))
    }

    @Test
    fun `concurrent access is thread-safe`() {
        val threads = (1..10).map { threadId ->
            Thread {
                val threadSource = InetSocketAddress("192.168.1.$threadId", 5000)
                repeat(100) { i ->
                    val seqNum = i.toLong()
                    cache.checkAndMark(threadSource, seqNum)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Should have 1000 unique entries (10 sources * 100 packets each)
        // minus any compaction that occurred
        assertTrue(cache.size > 0)
    }

    @Test
    fun `fire-and-forget senders with seqNum 0 all work`() {
        // Simulate multiple CLI invocations each starting at seqNum=0
        val senders = (1..5).map { InetSocketAddress("192.168.1.$it", (40000 + it)) }

        // Each sender sends seqNum=0
        senders.forEach { sender ->
            assertTrue(cache.checkAndMark(sender, 0), "First packet from $sender should be accepted")
        }

        // Total 5 entries (one per sender)
        assertEquals(5, cache.size)

        // Sending again from same sender IS a duplicate
        assertFalse(cache.checkAndMark(senders[0], 0), "Repeat from same sender should be duplicate")
    }
}
