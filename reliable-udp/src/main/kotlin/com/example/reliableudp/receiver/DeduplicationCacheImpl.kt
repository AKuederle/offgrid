package com.example.reliableudp.receiver

import com.example.reliableudp.ReliableUdpConstants
import java.util.concurrent.ConcurrentHashMap

/**
 * Deduplication cache using LRU-style eviction.
 *
 * Tracks seen sequence numbers to prevent duplicate packet delivery.
 * Uses approximate LRU by tracking insertion order and evicting oldest
 * entries when capacity is exceeded.
 */
class DeduplicationCacheImpl(
    private val maxSize: Int = ReliableUdpConstants.DEDUP_CACHE_SIZE
) : DeduplicationCache {

    // Using ConcurrentHashMap for thread safety
    // Value is insertion timestamp for LRU ordering
    private val seen = ConcurrentHashMap<Long, Long>()

    override fun checkAndMark(sequenceNumber: Long): Boolean {
        val now = System.nanoTime()

        // putIfAbsent returns null if key was not present (new packet)
        val existing = seen.putIfAbsent(sequenceNumber, now)

        if (existing != null) {
            // Already seen - duplicate
            return false
        }

        // New packet - check if we need to compact
        if (seen.size > maxSize) {
            compact()
        }

        return true
    }

    override fun compact() {
        if (seen.size <= maxSize) return

        // Remove oldest 25% of entries
        val toRemove = seen.size - (maxSize * 3 / 4)
        if (toRemove <= 0) return

        // Sort by timestamp and remove oldest
        seen.entries
            .sortedBy { it.value }
            .take(toRemove)
            .forEach { seen.remove(it.key) }
    }

    override val size: Int
        get() = seen.size
}
