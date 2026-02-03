# Research: Buffered Send with Persistent Retry

**Feature**: 004-buffered-send
**Date**: 2026-02-02

## Research Topics

### 1. Exponential Backoff Strategy

**Decision**: Use standard exponential backoff with base 1 second, doubling each attempt

**Rationale**:
- Industry standard for network retry (TCP, HTTP, gRPC all use variants)
- Backoff sequence: 1s, 2s, 4s, 8s, 16s, 32s = 63 seconds total
- 6 retry attempts covers the ~60 second requirement
- Simple to implement and understand

**Alternatives considered**:
- Linear backoff: Rejected - doesn't adapt well to network conditions
- Jittered backoff: Not needed - not dealing with thundering herd problem
- Configurable backoff: Over-engineering for this use case

### 2. Message Status State Machine

**Decision**: 5-state machine with clear transitions

**Rationale**:
- States map directly to user-visible conditions
- Transitions are deterministic and testable
- States: PENDING → SENDING → DELIVERED | RETRYING → WAITING

**State transitions**:
```
PENDING ──send()──→ SENDING
SENDING ──ack()───→ DELIVERED (terminal)
SENDING ──fail()──→ RETRYING
RETRYING ─retry()─→ SENDING
RETRYING ─timeout()→ WAITING
WAITING ──peer()──→ PENDING (resume cycle)
```

**Alternatives considered**:
- Fewer states (combine RETRYING/WAITING): Rejected - loses visibility into retry progress
- More states (per-retry states): Over-engineering

### 3. Presence Broadcast Implementation

**Decision**: Use UDP broadcast to port 5000 with a special packet type

**Rationale**:
- UDP broadcast reaches all devices on local subnet
- Reuses existing reliable-udp protocol infrastructure
- Add new PacketType.PRESENCE (value 0x03) to protocol
- No payload needed - just header with presence type

**Implementation details**:
- Broadcast address: 255.255.255.255 or subnet broadcast (192.168.1.255)
- Rate limiting: Max once per 30 seconds to prevent storms
- Triggered by: Foreground service start, network connectivity change

**Alternatives considered**:
- mDNS/Bonjour: Over-engineering for simple presence
- Periodic heartbeat: Wastes bandwidth when not needed
- Multicast: More complex, same effect as broadcast for local network

### 4. Kotlin CLI Tool Architecture

**Decision**: New `udp-cli` module using kotlinx-cli for argument parsing

**Rationale**:
- Pure Kotlin JVM module, no Android dependencies
- Directly uses reliable-udp library (same protocol)
- Single source of truth for protocol implementation
- Easy to build and run: `./gradlew :udp-cli:run --args="..."`

**Dependencies**:
- kotlinx-cli: Official Kotlin CLI argument parser
- reliable-udp: Existing protocol library
- kotlinx-coroutines: For async operations

**Commands**:
- `send -h HOST -p PORT -m MESSAGE` - Send single message
- `receive -p PORT` - Listen for messages
- `broadcast -p PORT` - Send presence broadcast

**Alternatives considered**:
- Keep Python tool: Rejected - duplicates protocol logic
- Clikt library: Similar to kotlinx-cli, no strong preference
- No CLI (just use tests): Harder to debug manually

### 5. Room Database Schema Extension

**Decision**: Add new `outbound_messages` table to existing Room database

**Rationale**:
- Reuses existing Room database infrastructure
- Single database for all message data (inbound + outbound)
- Type converters already exist for enums

**Schema**:
```kotlin
@Entity(tableName = "outbound_messages")
data class OutboundMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerHost: String,
    val peerPort: Int,
    val payload: ByteArray,
    val status: DeliveryStatus,
    val retryCount: Int,
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val deliveredAt: Long?
)
```

**Alternatives considered**:
- Separate database: Over-engineering, harder to query
- File-based storage: Loses Room benefits (transactions, queries)
- In-memory only: Doesn't survive app restart

### 6. Peer Activity Detection

**Decision**: Track peer activity by source address in existing receive path

**Rationale**:
- Already receiving packets with source address
- Simple map: peerAddress → lastSeenTimestamp
- On receive: update timestamp, trigger pending message check

**Integration point**: In `UdpSocket.processReceivedMessage()`, after message is processed, check if we have pending messages for this peer.

**Alternatives considered**:
- Separate peer tracking service: Over-engineering
- Database-backed peer tracking: Not needed for in-memory last-seen

### 7. Protocol Extension for Presence

**Decision**: Add `PRESENCE = 0x03` to existing PacketType enum in reliable-udp

**Rationale**:
- Minimal protocol change
- Reuses existing header format
- Presence packet: just header, no payload needed
- Header already has messageId = 0, seqNum = 0, fragment = 1/1

**Wire format**: Same 16-byte header, type=0x03, zero payload

**Alternatives considered**:
- Separate protocol: Over-engineering
- Magic bytes instead of type: Less clean than using existing header
