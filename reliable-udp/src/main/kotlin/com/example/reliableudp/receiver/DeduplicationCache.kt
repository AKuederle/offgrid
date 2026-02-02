package com.example.reliableudp.receiver

import java.net.InetSocketAddress

/**
 * Prevents duplicate packet delivery by tracking seen sequence numbers per sender.
 *
 * Uses LRU eviction to bound memory usage while preventing recent duplicates.
 * Tracks duplicates per source address to handle multiple senders that may
 * use overlapping sequence numbers.
 */
interface DeduplicationCache {
    /**
     * Check if packet was already seen and mark as seen.
     *
     * @param source The source address of the packet sender
     * @param sequenceNumber The packet sequence number to check
     * @return true if this is a NEW packet, false if duplicate
     */
    fun checkAndMark(source: InetSocketAddress, sequenceNumber: Long): Boolean

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
