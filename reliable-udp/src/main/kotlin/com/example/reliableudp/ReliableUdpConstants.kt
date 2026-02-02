package com.example.reliableudp

/**
 * Constants for the reliable UDP transport protocol.
 *
 * Based on QUIC RFC 9002 recommendations for loss detection and RTT estimation.
 */
object ReliableUdpConstants {
    // Protocol header sizes
    const val HEADER_SIZE = 11                    // bytes: type(1) + msgId(4) + seqNum(4) + fragIdx(1) + fragTotal(1)
    const val MAX_PAYLOAD_SIZE = 1400             // bytes (MTU 1500 - IP/UDP headers - safety margin)
    const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
    const val MAX_MESSAGE_SIZE = 64 * 1024        // 64KB maximum message size

    // RTT estimation (in nanoseconds, per RFC 9002)
    const val INITIAL_RTT_NS = 333_000_000L       // 333ms initial RTT estimate
    const val GRANULARITY_NS = 1_000_000L         // 1ms timer granularity

    // Loss detection thresholds (per RFC 9002)
    const val PACKET_THRESHOLD = 3                // packet reordering tolerance
    const val TIME_THRESHOLD_NUM = 9              // 9/8 multiplier numerator
    const val TIME_THRESHOLD_DEN = 8              // 9/8 multiplier denominator

    // Retransmission limits
    const val MAX_RETRIES = 10                    // maximum retransmit attempts
    const val MAX_PTO_BACKOFF = 6                 // 2^6 = 64x maximum backoff

    // Buffer capacities
    const val ACK_QUEUE_CAPACITY = 1000           // pending ACKs to batch
    const val FRAGMENT_TIMEOUT_MS = 30_000L       // 30 seconds fragment reassembly timeout
    const val DEDUP_CACHE_SIZE = 10_000           // packet sequence numbers to remember

    // Validation
    const val MAX_FRAGMENT_INDEX = 255            // max fragments per message (1 byte)
}
