package com.example.reliableudp.receiver

/**
 * Prevents duplicate packet delivery by tracking seen sequence numbers.
 *
 * Uses LRU eviction to bound memory usage while preventing recent duplicates.
 */
interface DeduplicationCache {
    /**
     * Check if packet was already seen and mark as seen.
     *
     * @param sequenceNumber The packet sequence number to check
     * @return true if this is a NEW packet, false if duplicate
     */
    fun checkAndMark(sequenceNumber: Long): Boolean

    /**
     * Clear old entries to bound memory usage.
     *
     * Called periodically to remove entries that are unlikely to be duplicated.
     */
    fun compact()

    /**
     * Current number of entries in the cache.
     */
    val size: Int
}
