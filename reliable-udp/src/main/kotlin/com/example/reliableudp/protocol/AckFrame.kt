package com.example.reliableudp.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a contiguous range of acknowledged packet sequence numbers.
 *
 * @property smallest Lower bound (inclusive)
 * @property largest Upper bound (inclusive)
 */
data class AckRange(
    val smallest: Long,
    val largest: Long
) {
    init {
        require(smallest <= largest) {
            "Invalid range: smallest ($smallest) > largest ($largest)"
        }
    }

    /** Check if a sequence number is within this range */
    fun contains(sequenceNumber: Long): Boolean = sequenceNumber in smallest..largest

    /** Number of sequence numbers in this range */
    val size: Int get() = (largest - smallest + 1).toInt()
}

/**
 * Selective ACK (SACK) frame for acknowledging received packets.
 *
 * Wire format:
 * ```
 * ┌─────────────────┬────────────┬────────────┬─────────────────┐
 * │ LargestAcked(4) │ Delay(2)   │ Count(1)   │ Ranges...       │
 * └─────────────────┴────────────┴────────────┴─────────────────┘
 *
 * Each Range (4 bytes):
 * ┌─────────────────────────────────┐
 * │ GapSize(2) │ AckCount(2)        │
 * └─────────────────────────────────┘
 * ```
 *
 * Encoding works backward from largestAcked:
 * - First range: starts at largestAcked, extends backward by ackCount
 * - Gap: number of missing packets before next range
 * - Next range: starts after gap, extends backward by ackCount
 *
 * @property largestAcked Highest packet sequence number being acknowledged
 * @property ackDelayMicros Time in microseconds since largestAcked was received
 * @property ranges List of contiguous acknowledged ranges, sorted descending by largest
 */
data class AckFrame(
    val largestAcked: Long,
    val ackDelayMicros: Int,
    val ranges: List<AckRange>
) {
    init {
        require(largestAcked >= 0) { "largestAcked must be non-negative" }
        require(ackDelayMicros >= 0) { "ackDelayMicros must be non-negative" }
        require(ranges.isNotEmpty()) { "ACK frame must have at least one range" }
        require(ranges.first().largest == largestAcked) {
            "First range largest (${ranges.first().largest}) must equal largestAcked ($largestAcked)"
        }
    }

    /** Check if a sequence number is acknowledged by this frame */
    fun contains(sequenceNumber: Long): Boolean = ranges.any { it.contains(sequenceNumber) }

    /**
     * Serialize to wire format bytes.
     *
     * @return Byte array containing the ACK frame
     */
    fun toBytes(): ByteArray {
        // Header: 4 (largestAcked) + 2 (delay) + 1 (count) = 7 bytes
        // Each range: 2 (gap) + 2 (ackCount) = 4 bytes
        val size = 7 + (ranges.size * 4)
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(largestAcked.toInt())
        buffer.putShort(ackDelayMicros.toShort())
        buffer.put(ranges.size.toByte())

        // Encode ranges from largest to smallest
        var previousSmallest = largestAcked + 1
        for (range in ranges) {
            // Gap: packets between previous range's smallest and this range's largest
            val gap = (previousSmallest - range.largest - 1).toInt()
            // AckCount: number of packets in this range minus 1 (for encoding efficiency)
            val ackCount = range.size - 1

            buffer.putShort(gap.toShort())
            buffer.putShort(ackCount.toShort())

            previousSmallest = range.smallest
        }

        return buffer.array()
    }

    companion object {
        /**
         * Parse ACK frame from wire format bytes.
         *
         * @param data Byte array containing the ACK frame
         * @param offset Starting position in array (default 0)
         * @return Parsed AckFrame, or null if invalid
         */
        fun fromBytes(data: ByteArray, offset: Int = 0): AckFrame? {
            if (data.size - offset < 7) return null

            val buffer = ByteBuffer.wrap(data, offset, data.size - offset)
                .order(ByteOrder.BIG_ENDIAN)

            val largestAcked = buffer.int.toLong() and 0xFFFFFFFFL
            val ackDelayMicros = buffer.short.toInt() and 0xFFFF
            val rangeCount = buffer.get().toInt() and 0xFF

            if (rangeCount == 0) return null
            if (data.size - offset < 7 + (rangeCount * 4)) return null

            val ranges = mutableListOf<AckRange>()
            var currentLargest = largestAcked

            for (i in 0 until rangeCount) {
                val gap = buffer.short.toInt() and 0xFFFF
                val ackCount = buffer.short.toInt() and 0xFFFF

                // First range: no gap before it
                val rangeLargest = if (i == 0) currentLargest else currentLargest - gap - 1
                val rangeSmallest = rangeLargest - ackCount

                if (rangeSmallest < 0) return null

                ranges.add(AckRange(rangeSmallest, rangeLargest))
                currentLargest = rangeSmallest
            }

            return AckFrame(
                largestAcked = largestAcked,
                ackDelayMicros = ackDelayMicros,
                ranges = ranges
            )
        }

        /**
         * Build ACK frame from a collection of received packet sequence numbers.
         *
         * Coalesces individual packet numbers into contiguous ranges.
         *
         * @param packetNumbers Collection of received sequence numbers
         * @param ackDelayMicros Time since largest packet was received
         * @return AckFrame, or null if collection is empty
         */
        fun fromPacketNumbers(
            packetNumbers: Collection<Long>,
            ackDelayMicros: Int = 0
        ): AckFrame? {
            if (packetNumbers.isEmpty()) return null

            val sorted = packetNumbers.sorted()
            val ranges = mutableListOf<AckRange>()

            var rangeStart = sorted.first()
            var rangeEnd = rangeStart

            for (pn in sorted.drop(1)) {
                if (pn == rangeEnd + 1) {
                    // Extend current range
                    rangeEnd = pn
                } else {
                    // Gap detected - finalize current range and start new one
                    ranges.add(AckRange(rangeStart, rangeEnd))
                    rangeStart = pn
                    rangeEnd = pn
                }
            }
            // Add final range
            ranges.add(AckRange(rangeStart, rangeEnd))

            // Reverse to get descending order (largest first)
            ranges.reverse()

            return AckFrame(
                largestAcked = sorted.last(),
                ackDelayMicros = ackDelayMicros,
                ranges = ranges
            )
        }
    }
}
