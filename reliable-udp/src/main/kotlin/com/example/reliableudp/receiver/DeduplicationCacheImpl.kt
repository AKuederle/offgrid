package com.example.reliableudp.receiver

import com.example.reliableudp.ReliableUdpConstants
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Deduplication cache using LRU-style eviction.
 *
 * Tracks seen sequence numbers per sender to prevent duplicate packet delivery.
 * Uses approximate LRU by tracking insertion order and evicting oldest
 * entries when capacity is exceeded.
 *
 * Key insight: Different senders may use overlapping sequence numbers
 * (e.g., both starting at 0), so we must track (source, seqNum) pairs.
 */
class DeduplicationCacheImpl(
    private val maxSize: Int = ReliableUdpConstants.DEDUP_CACHE_SIZE
) : DeduplicationCache {

    /**
     * Key for deduplication: combines source address and sequence number.
     * Two packets are duplicates only if they have the same source AND seqNum.
     */
    private data class DedupeKey(
        val sourceHost: String,
        val sourcePort: Int,
        val sequenceNumber: Long
    )

    // Using ConcurrentHashMap for thread safety
    // Value is insertion timestamp for LRU ordering
    private val seen = ConcurrentHashMap<DedupeKey, Long>()

    override fun checkAndMark(source: InetSocketAddress, sequenceNumber: Long): Boolean {
        val key = DedupeKey(
            sourceHost = source.address?.hostAddress ?: source.hostString,
            sourcePort = source.port,
            sequenceNumber = sequenceNumber
        )
        val now = System.nanoTime()

        // putIfAbsent returns null if key was not present (new packet)
        val existing = seen.putIfAbsent(key, now)

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
