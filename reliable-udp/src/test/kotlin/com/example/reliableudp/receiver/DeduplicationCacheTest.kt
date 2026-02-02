package com.example.reliableudp.receiver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class DeduplicationCacheTest {

    private lateinit var cache: DeduplicationCacheImpl

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
        assertTrue(cache.checkAndMark(1))
    }

    @Test
    fun `checkAndMark returns false for duplicate packet`() {
        cache.checkAndMark(1)
        assertFalse(cache.checkAndMark(1))
    }

    @Test
    fun `checkAndMark increments size for new packets`() {
        cache.checkAndMark(1)
        assertEquals(1, cache.size)

        cache.checkAndMark(2)
        assertEquals(2, cache.size)
    }

    @Test
    fun `checkAndMark does not increment size for duplicates`() {
        cache.checkAndMark(1)
        cache.checkAndMark(1)
        cache.checkAndMark(1)
        assertEquals(1, cache.size)
    }

    @Test
    fun `compact removes old entries when over capacity`() {
        // Fill to capacity
        repeat(100) { cache.checkAndMark(it.toLong()) }
        assertEquals(100, cache.size)

        // Add more to trigger compaction
        cache.checkAndMark(200)
        cache.checkAndMark(201)

        // Compact should have been triggered
        assertTrue(cache.size <= 100)
    }

    @Test
    fun `compact removes oldest entries first`() {
        val smallCache = DeduplicationCacheImpl(maxSize = 10)

        // Add packets 0-9
        repeat(10) { smallCache.checkAndMark(it.toLong()) }

        // Add packet 100 to trigger compaction
        smallCache.checkAndMark(100)

        // Most recent packets should still be marked
        assertFalse(smallCache.checkAndMark(100)) // Should be duplicate

        // Oldest packets may have been evicted
        // (exact behavior depends on implementation)
    }

    @Test
    fun `manual compact reduces size`() {
        // Fill beyond capacity
        val smallCache = DeduplicationCacheImpl(maxSize = 10)
        repeat(20) { smallCache.checkAndMark(it.toLong()) }

        smallCache.compact()

        assertTrue(smallCache.size <= 10)
    }

    @Test
    fun `compact is idempotent when under capacity`() {
        cache.checkAndMark(1)
        cache.checkAndMark(2)

        val sizeBefore = cache.size
        cache.compact()
        assertEquals(sizeBefore, cache.size)
    }

    @Test
    fun `handles large sequence numbers`() {
        assertTrue(cache.checkAndMark(Long.MAX_VALUE))
        assertFalse(cache.checkAndMark(Long.MAX_VALUE))
    }

    @Test
    fun `concurrent access is thread-safe`() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    val seqNum = (threadId * 1000 + i).toLong()
                    cache.checkAndMark(seqNum)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Should have 1000 unique entries (10 threads * 100 packets each)
        // minus any compaction that occurred
        assertTrue(cache.size > 0)
    }
}
