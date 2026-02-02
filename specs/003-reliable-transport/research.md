# Research: Reliable Transport Layer

**Feature**: 003-reliable-transport
**Date**: 2026-02-01

## Overview

This document captures research findings for implementing a custom reliable UDP transport layer, based on analysis of the Quincy QUIC implementation and RFC 9002 loss detection algorithms.

## 1. Quincy PacketBuffer Pattern

**Source**: [Quincy PacketBuffer.java](https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/PacketBuffer.java)
**License**: Apache 2.0

### Design Decision

Use `ConcurrentHashMap<Long, SentPacket>` for thread-safe packet tracking.

**Rationale**: Simple key-value structure where packet sequence number is the key. Timestamps stored with each entry enable timeout-based cleanup.

**Alternatives Considered**:
- `MutableStateFlow<Map<...>>` - Rejected: unnecessary reactive overhead for internal bookkeeping
- `Mutex + mutableMapOf` - Acceptable alternative if we need suspend functions during access

### Key Data Structure

```kotlin
data class SentPacket(
    val packetNumber: Long,
    val messageId: Int,
    val fragmentIndex: Int,
    val payload: ByteArray,
    val sentAt: Long = System.nanoTime(),
    val retransmitCount: Int = 0
)

class PacketBuffer {
    private val buffer = ConcurrentHashMap<Long, SentPacket>()

    fun put(packet: SentPacket) { buffer[packet.packetNumber] = packet }
    fun remove(packetNumber: Long): SentPacket? = buffer.remove(packetNumber)
    fun drainOlderThan(timeoutNanos: Long): List<SentPacket> { /* ... */ }
}
```

## 2. Quincy AckQueue Pattern

**Source**: [Quincy AckQueue.java](https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/AckQueue.java)
**License**: Apache 2.0

### Design Decision

Use Kotlin `Channel<AckEntry>` instead of Java `BlockingQueue`.

**Rationale**: Channels integrate naturally with coroutines and provide backpressure handling. Capacity of 1000 entries matches Quincy's design.

**Alternatives Considered**:
- `BlockingQueue` - Rejected: requires blocking calls, not coroutine-friendly
- `MutableSharedFlow` - Rejected: no buffering guarantee, complex backpressure

### ACK Range Coalescing Algorithm

```kotlin
fun buildAckRanges(packetNumbers: Collection<Long>): List<AckRange> {
    val sorted = packetNumbers.sorted()
    if (sorted.isEmpty()) return emptyList()

    val ranges = mutableListOf<AckRange>()
    var lower = sorted.first()
    var upper = lower

    for (pn in sorted.drop(1)) {
        if (pn > upper + 1) {
            // Gap detected - finalize current range
            ranges.add(AckRange(lower, upper))
            lower = pn
        }
        upper = pn
    }
    ranges.add(AckRange(lower, upper))
    return ranges
}
```

## 3. RFC 9002 Loss Detection

**Source**: [RFC 9002](https://www.rfc-editor.org/rfc/rfc9002.html)

### Design Decision

Implement both packet threshold and time threshold loss detection.

**Rationale**: RFC 9002's dual approach handles both fast loss detection (when subsequent packets arrive) and timeout-based detection (when no traffic).

### Constants (from RFC 9002)

| Constant | Value | Purpose |
|----------|-------|---------|
| `kInitialRtt` | 333 ms | Initial RTT estimate before samples |
| `kGranularity` | 1 ms | Timer minimum granularity |
| `kPacketThreshold` | 3 | Packet reordering tolerance |
| `kTimeThreshold` | 9/8 (1.125) | RTT multiplier for time-based loss |

### Loss Detection Rules

1. **Packet Threshold**: Packet X is lost if 3+ later packets are acknowledged but X is not
2. **Time Threshold**: Packet X is lost if sent more than `max(smoothedRtt, latestRtt) * 9/8` ago

```kotlin
fun isLostByPacketThreshold(pn: Long, largestAcked: Long): Boolean {
    return pn <= largestAcked - kPacketThreshold
}

fun isLostByTimeThreshold(sentAt: Long, now: Long, rtt: Long): Boolean {
    val threshold = maxOf((rtt * 9) / 8, kGranularity)
    return now - sentAt > threshold
}
```

## 4. RTT Estimation

**Source**: RFC 9002 Section 5

### Design Decision

Use exponentially weighted moving average (EWMA) for RTT estimation.

**Rationale**: Standard approach from TCP/QUIC, proven effective for adaptive timeout calculation.

### Algorithm

```kotlin
class RttEstimator(
    private var smoothedRtt: Long = 333_000_000L,  // 333ms in nanos
    private var rttVar: Long = 166_500_000L,       // smoothedRtt / 2
    private var minRtt: Long = Long.MAX_VALUE
) {
    fun update(latestRtt: Long, ackDelay: Long) {
        minRtt = minOf(minRtt, latestRtt)

        // Adjust for ACK delay only if above minimum
        val adjustedRtt = if (latestRtt - minRtt >= ackDelay) {
            latestRtt - ackDelay
        } else {
            latestRtt
        }

        // EWMA update
        val rttvarSample = abs(smoothedRtt - adjustedRtt)
        rttVar = (3 * rttVar + rttvarSample) / 4
        smoothedRtt = (7 * smoothedRtt + adjustedRtt) / 8
    }

    fun getPto(maxAckDelay: Long = 0): Long {
        return smoothedRtt + maxOf(4 * rttVar, kGranularity) + maxAckDelay
    }
}
```

## 5. Probe Timeout (PTO) Strategy

### Design Decision

Use adaptive PTO with exponential backoff instead of fixed 200ms timeout.

**Rationale**: Fixed timeout is too aggressive for high-latency networks and too slow for fast networks. PTO adapts to measured RTT.

**Alternatives Considered**:
- Fixed 200ms (spec default) - Rejected: not adaptive to network conditions
- Fixed 1000ms (Quincy default) - Rejected: too slow for LAN use case

### Implementation

```kotlin
// Initial PTO before RTT samples
val initialPto = kInitialRtt + 4 * (kInitialRtt / 2) // ~999ms

// After samples, PTO = smoothed_rtt + max(4*rtt_var, 1ms) + max_ack_delay
// For our use case, max_ack_delay = 0 (immediate ACKs)

// Backoff on consecutive timeouts
fun getBackedOffPto(basePtr: Long, consecutiveTimeouts: Int): Long {
    return basePto * (1L shl minOf(consecutiveTimeouts, 6)) // Cap at 64x
}
```

## 6. Fragmentation Strategy

### Design Decision

Fragment at 1400 bytes to stay below typical MTU (1500 - IP/UDP headers).

**Rationale**: Avoids IP fragmentation which causes all-or-nothing delivery.

### Fragment Header

Each fragment carries:
- `messageId` (4 bytes): Groups fragments of same logical message
- `fragmentIndex` (1 byte): 1-based position in message
- `fragmentTotal` (1 byte): Total fragments in message

### Reassembly Buffer

```kotlin
class FragmentBuffer {
    // messageId -> (fragmentIndex -> payload)
    private val pending = ConcurrentHashMap<Int, ConcurrentHashMap<Int, ByteArray>>()
    private val metadata = ConcurrentHashMap<Int, FragmentMetadata>()

    data class FragmentMetadata(
        val total: Int,
        val firstSeenAt: Long = System.currentTimeMillis()
    )

    fun addFragment(msgId: Int, fragIdx: Int, fragTotal: Int, payload: ByteArray): ByteArray? {
        // Returns complete message when all fragments received, null otherwise
    }

    // Cleanup: discard incomplete messages after 30s
}
```

## 7. Coroutine Integration

### Design Decision

Use structured concurrency with `CoroutineScope` for all async operations.

**Rationale**: Ensures proper cleanup when socket is closed. Parent scope cancellation propagates to all child jobs.

### Timer Pattern

```kotlin
private fun startRetransmitLoop() = scope.launch {
    while (isActive) {
        delay(10) // 10ms check interval
        val now = System.nanoTime()
        val pto = rttEstimator.getPto()

        packetBuffer.drainOlderThan(pto).forEach { packet ->
            if (packet.retransmitCount < maxRetries) {
                retransmit(packet)
            } else {
                onDeliveryFailed(packet.messageId)
            }
        }
    }
}
```

## 8. Thread Safety Model

### Design Decision

Use `ConcurrentHashMap` for shared state, avoid `Mutex` where possible.

**Rationale**: `ConcurrentHashMap` provides lock-free reads and fine-grained locking for writes, better than `Mutex` for high-throughput scenarios.

### Synchronization Points

| Component | Thread Safety | Rationale |
|-----------|--------------|-----------|
| PacketBuffer | ConcurrentHashMap | Frequent reads during ACK processing |
| AckQueue | Channel(1000) | Natural coroutine integration |
| FragmentBuffer | ConcurrentHashMap | Fragments may arrive on different threads |
| RttEstimator | Mutex | Infrequent updates, needs atomic read-modify-write |
| SeqNum counter | AtomicLong | Simple increment operation |

## Summary

All research questions resolved. Key decisions:
1. **PacketBuffer**: ConcurrentHashMap with timestamp-based cleanup
2. **AckQueue**: Kotlin Channel with 1000 capacity
3. **Loss Detection**: Dual threshold (packet + time) from RFC 9002
4. **RTT**: EWMA with PTO calculation
5. **Fragmentation**: 1400-byte threshold, 30s reassembly timeout
6. **Concurrency**: Structured coroutines, ConcurrentHashMap for shared state
