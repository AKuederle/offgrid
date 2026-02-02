# Data Model: Reliable Transport Layer

**Feature**: 003-reliable-transport
**Date**: 2026-02-01

## Overview

This document defines the data entities for the reliable UDP transport layer. All entities are in-memory only (no persistence required).

## Entities

### 1. ReliablePacket

The wire format for all reliable transport packets.

```kotlin
/**
 * Wire format:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Type (1) │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
 * ├──────────┴──────────┴───────────┴───────────┴──────────────┤
 * │ Payload (variable length)                                   │
 * └─────────────────────────────────────────────────────────────┘
 * Total header: 11 bytes
 */
data class ReliablePacket(
    val type: PacketType,
    val messageId: Int,           // Unique per logical message (for reassembly)
    val sequenceNumber: Long,     // Strictly increasing, never reused
    val fragmentIndex: Int,       // 1-based position (1 if single packet)
    val fragmentTotal: Int,       // Total fragments (1 if unfragmented)
    val payload: ByteArray
)
```

**Field Constraints**:
| Field | Type | Range | Notes |
|-------|------|-------|-------|
| type | Byte | 0x01-0x03 | DATA, ACK, PING |
| messageId | Int | 0 to Int.MAX_VALUE | Wraps on overflow |
| sequenceNumber | Long | 0 to Long.MAX_VALUE | Never reused within session |
| fragmentIndex | Int | 1 to 255 | 1-based for readability |
| fragmentTotal | Int | 1 to 255 | Max 255 fragments per message |
| payload | ByteArray | 0 to 1400 bytes | After header |

### 2. PacketType

Enumeration of packet types.

```kotlin
enum class PacketType(val value: Byte) {
    DATA(0x01),    // Payload packet, requires ACK
    ACK(0x02),     // Acknowledgment with SACK ranges
    PING(0x03);    // Keep-alive / RTT measurement

    companion object {
        fun fromByte(value: Byte): PacketType? = entries.find { it.value == value }
    }
}
```

### 3. AckFrame

Selective ACK packet payload format.

```kotlin
/**
 * Wire format:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ LargestAcked(4) │ AckDelay(2) │ RangeCount(1) │ Ranges...   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Each Range (4 bytes):
 * ┌─────────────────────────────────┐
 * │ GapSize(2) │ AckCount(2)        │
 * └─────────────────────────────────┘
 *
 * Encoding example: "Received 1-5, 8-10, 12"
 *   LargestAcked=12
 *   Ranges (from largest, working backward):
 *     - (gap=0, ack=1)   → 12
 *     - (gap=1, ack=3)   → skip 11, ack 10,9,8
 *     - (gap=2, ack=5)   → skip 7,6, ack 5,4,3,2,1
 */
data class AckFrame(
    val largestAcked: Long,       // Highest packet number acknowledged
    val ackDelayMicros: Int,      // Time since largest packet received
    val ranges: List<AckRange>    // Contiguous ranges, descending order
)

data class AckRange(
    val smallest: Long,           // Lower bound (inclusive)
    val largest: Long             // Upper bound (inclusive)
) {
    init {
        require(smallest <= largest) { "Invalid range: $smallest > $largest" }
    }

    fun contains(pn: Long): Boolean = pn in smallest..largest
    val size: Int get() = (largest - smallest + 1).toInt()
}
```

**Encoding Rules**:
- Ranges stored descending (largest first)
- Gap = number of missing packets before this range
- AckCount = number of contiguous packets in range (minus 1 for encoding efficiency)

### 4. SentPacket

Tracks packets awaiting acknowledgment.

```kotlin
data class SentPacket(
    val sequenceNumber: Long,
    val messageId: Int,
    val fragmentIndex: Int,
    val fragmentTotal: Int,
    val payload: ByteArray,
    val destination: InetSocketAddress,
    val sentAt: Long = System.nanoTime(),
    val retransmitCount: Int = 0
) {
    /**
     * Create a retransmission with new sequence number and timestamp.
     */
    fun retransmit(newSeqNum: Long): SentPacket = copy(
        sequenceNumber = newSeqNum,
        sentAt = System.nanoTime(),
        retransmitCount = retransmitCount + 1
    )
}
```

**State Transitions**:
```
[Created] --send--> [InFlight] --ack--> [Confirmed]
                        |
                        +--timeout--> [Retransmit] --send--> [InFlight]
                        |
                        +--max_retries--> [Failed]
```

### 5. ReceivedPacket

Tracks received packets for ACK generation.

```kotlin
data class ReceivedPacket(
    val sequenceNumber: Long,
    val receivedAt: Long = System.nanoTime()
)
```

### 6. PendingFragment

Tracks fragments awaiting reassembly.

```kotlin
data class PendingFragment(
    val messageId: Int,
    val fragmentIndex: Int,
    val fragmentTotal: Int,
    val payload: ByteArray,
    val receivedAt: Long = System.currentTimeMillis()
)

data class FragmentAssembly(
    val messageId: Int,
    val expectedTotal: Int,
    val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
    val firstSeenAt: Long = System.currentTimeMillis()
) {
    val isComplete: Boolean get() = fragments.size == expectedTotal

    fun addFragment(index: Int, payload: ByteArray): Boolean {
        fragments[index] = payload
        return isComplete
    }

    fun assemble(): ByteArray {
        require(isComplete) { "Cannot assemble incomplete message" }
        return (1..expectedTotal)
            .map { fragments[it]!! }
            .reduce { acc, bytes -> acc + bytes }
    }
}
```

### 7. RttSample

RTT measurement data.

```kotlin
data class RttSample(
    val latestRtt: Long,          // Nanoseconds
    val ackDelay: Long,           // Nanoseconds (from ACK frame)
    val sampledAt: Long = System.nanoTime()
)

data class RttState(
    val smoothedRtt: Long,        // EWMA of RTT samples (nanos)
    val rttVariance: Long,        // RTT variance (nanos)
    val minRtt: Long              // Minimum observed RTT (nanos)
)
```

### 8. DeliveryResult

Callback result for message delivery status.

```kotlin
sealed class DeliveryResult {
    data class Success(
        val messageId: Int,
        val deliveredAt: Long = System.currentTimeMillis()
    ) : DeliveryResult()

    data class Failure(
        val messageId: Int,
        val reason: FailureReason,
        val retryCount: Int
    ) : DeliveryResult()

    enum class FailureReason {
        MAX_RETRIES_EXCEEDED,
        TIMEOUT,
        SOCKET_CLOSED
    }
}
```

## Constants

```kotlin
object ReliableUdpConstants {
    // Protocol
    const val HEADER_SIZE = 11                    // bytes
    const val MAX_PAYLOAD_SIZE = 1400             // bytes (MTU - headers)
    const val MAX_PACKET_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE
    const val MAX_MESSAGE_SIZE = 64 * 1024        // 64KB

    // RTT (in nanoseconds)
    const val INITIAL_RTT_NS = 333_000_000L       // 333ms
    const val GRANULARITY_NS = 1_000_000L         // 1ms

    // Loss detection
    const val PACKET_THRESHOLD = 3                // reordering tolerance
    const val TIME_THRESHOLD_NUM = 9              // 9/8 multiplier
    const val TIME_THRESHOLD_DEN = 8

    // Retransmission
    const val MAX_RETRIES = 10
    const val MAX_PTO_BACKOFF = 6                 // 2^6 = 64x max

    // Buffers
    const val ACK_QUEUE_CAPACITY = 1000
    const val FRAGMENT_TIMEOUT_MS = 30_000L       // 30 seconds
    const val DEDUP_CACHE_SIZE = 10_000           // packet numbers to remember
}
```

## Validation Rules

| Entity | Rule | Error |
|--------|------|-------|
| ReliablePacket | payload.size <= MAX_PAYLOAD_SIZE | PayloadTooLargeException |
| ReliablePacket | fragmentIndex in 1..fragmentTotal | InvalidFragmentException |
| AckRange | smallest <= largest | InvalidRangeException |
| FragmentAssembly | fragmentTotal <= 255 | TooManyFragmentsException |
| Message | size <= MAX_MESSAGE_SIZE | MessageTooLargeException |

## Relationships

```
Message (user data)
    │
    ├── fragments into ──► [ReliablePacket] 1..N
    │                           │
    │                           ├── tracked by ──► [SentPacket] (sender)
    │                           │
    │                           └── received as ──► [ReceivedPacket] (receiver)
    │                                                   │
    │                                                   └── batched into ──► [AckFrame]
    │
    └── reassembled from ──► [FragmentAssembly]
                                 │
                                 └── contains ──► [PendingFragment] 1..N
```
