# Feature Specification: Reliable Transport Layer

**Feature Branch**: `003-reliable-transport`
**Created**: 2026-02-01
**Status**: Draft
**Input**: User description: "Custom reliable UDP implementation with ARQ, fragmentation, and parallel packet sending - borrowing concepts from QUIC"

## Overview

Implement a custom reliable transport layer on top of UDP, borrowing proven concepts from QUIC (Selective Repeat ARQ, packet numbering, SACK). This replaces the current raw `DatagramSocket` with a reliability layer that provides:

- **Automatic retry** via Selective Repeat ARQ
- **Large message support** via fragmentation/reassembly
- **Parallel packet sending** for throughput
- **Delivery confirmation** via Selective ACK (SACK)

### Why Custom Implementation?

Existing libraries have blockers:
- [seniorjoinu/reliable-udp](https://github.com/seniorjoinu/reliable-udp): Android ARM native library loading fails ([Issue #3](https://github.com/seniorjoinu/reliable-udp/issues/3))
- [java-Kcp](https://github.com/l42111996/java-Kcp): Connection-oriented, heavy Netty dependency
- Other RUDP libraries: Connection-based or unmaintained

By implementing ourselves, we get:
- Full control over the wire protocol
- No native library dependencies
- Kotlin coroutine-native design
- Tailored to our connectionless use case

### Dedicated Library Module

The reliable transport implementation will be a **standalone Gradle module** (`:reliable-udp`) that can be:
- Used independently of the `udp-service` module
- Published as a separate artifact if desired
- Tested in isolation with unit tests

```
android-udp-service/
├── reliable-udp/          # NEW: Standalone reliable transport library
│   ├── src/main/kotlin/
│   └── src/test/kotlin/
├── udp-service/           # Existing: Uses reliable-udp as dependency
└── app/                   # Existing: Demo app
```

### Code Attribution Requirements

When borrowing or adapting code from external sources:

1. **Source reference**: Include a comment with the original file URL
2. **License reference**: Include the license type and link to LICENSE file
3. **Modification note**: Document what was changed from the original

Example:
```kotlin
/**
 * Adapted from Quincy QUIC implementation.
 *
 * Original: https://github.com/protocol7/quincy/blob/master/quic/src/.../PacketBuffer.java
 * License: Apache 2.0 (https://github.com/protocol7/quincy/blob/master/LICENSE)
 *
 * Modifications:
 * - Ported from Java to Kotlin
 * - Simplified for connectionless UDP (removed encryption levels)
 * - Added coroutine support
 */
```

### Why UDP (Not TCP)?

- **Connectionless**: No handshake; devices send messages immediately
- **No TLS/certificates**: Security handled at app layer with payload encryption
- **Simpler NAT traversal**: No connection state to maintain
- **Future multicast potential**: UDP enables one-to-many broadcasting

## Technical Design

### Wire Protocol

Borrowing from [QUIC RFC 9002](https://quicwg.org/base-drafts/rfc9002.html):

```
┌─────────────────────────────────────────────────────────────┐
│ Reliable UDP Header (12 bytes)                              │
├──────────┬──────────┬───────────┬───────────┬──────────────┤
│ Type (1) │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
│          │          │           │ (1-based) │              │
├──────────┴──────────┴───────────┴───────────┴──────────────┤
│ Payload (existing appId prefix + data)                      │
└─────────────────────────────────────────────────────────────┘

Packet Types:
  0x01 = DATA      - Payload packet (requires ACK)
  0x02 = ACK       - Acknowledgment with SACK ranges
  0x03 = PING      - Keep-alive / RTT measurement

MsgID:    Unique per logical message (for reassembly)
SeqNum:   Strictly increasing, never reused (QUIC-style)
FragIdx:  1-N for fragments, 1 if single packet
FragTotal: Total fragments in message (1 if unfragmented)
```

### ACK Packet Format (SACK)

```
┌─────────────────────────────────────────────────────────────┐
│ Type=0x02 │ LargestAcked(4) │ AckRangeCount(1) │ Ranges... │
├───────────┴─────────────────┴──────────────────┴───────────┤
│ Each Range: GapSize(2) + AckCount(2) = 4 bytes             │
└─────────────────────────────────────────────────────────────┘

Example: "Received 1-5, 8-10, 12" encoded as:
  LargestAcked=12, Ranges=[(gap=1, ack=1), (gap=2, ack=3), (gap=0, ack=5)]
```

### Key Components (Borrowing from [Quincy](https://github.com/protocol7/quincy))

| Component | Purpose | Reference |
|-----------|---------|-----------|
| `PacketBuffer` | Track sent-but-unacked packets with timestamps | [Quincy PacketBuffer.java](https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/PacketBuffer.java) |
| `AckQueue` | Queue received packet numbers for batched ACK | [Quincy AckQueue.java](https://github.com/protocol7/quincy/blob/master/quic/src/main/java/com/protocol7/quincy/reliability/AckQueue.java) |
| `FragmentBuffer` | Reassemble out-of-order fragments per MsgID | Custom |
| `RetransmitTimer` | Trigger retransmission on timeout | Based on QUIC PTO |

### Reliability Mechanisms

**From [QUIC Loss Detection](https://www.rfc-editor.org/rfc/rfc9002.html):**

1. **Strictly increasing packet numbers**: Every packet (including retransmits) gets a new SeqNum - eliminates ACK ambiguity
2. **Selective ACK (SACK)**: Receiver reports ranges of received packets - sender retransmits only missing ones
3. **Fast retransmit**: If 3 packets after X are ACKed but X isn't, assume X is lost
4. **Timeout retransmit**: RTO-based fallback (default: 200ms, adaptive based on RTT)

**Fragmentation:**
1. Sender fragments messages >1400 bytes into MTU-sized chunks
2. All fragments sent in parallel (same MsgID, different FragIdx)
3. Receiver buffers fragments, delivers when FragTotal received
4. Each fragment individually ACKed and retransmitted if lost

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reliable Delivery Under Loss (Priority: P1)

As a sender, I want my packets to be reliably delivered even when the network drops packets.

**Why this priority**: Core reliability guarantee is the primary value.

**Independent Test**: Adapted from [QUIC Interop Runner](https://github.com/quic-interop/quic-interop-runner):
- Send 50 messages over simulated 30% packet loss
- Verify all 50 messages received intact

**Acceptance Scenarios**:

1. **Given** 10% packet loss, **When** sender sends 100 packets, **Then** receiver gets all 100 (via retransmission)
2. **Given** packet X is lost, **When** packets X+1, X+2, X+3 arrive, **Then** sender fast-retransmits X
3. **Given** packet X is lost and no subsequent packets, **When** RTO expires, **Then** sender retransmits X

---

### User Story 2 - Large Message Fragmentation (Priority: P1)

As a sender, I want to send messages larger than MTU without manual chunking.

**Why this priority**: Simplifies API for users sending images, files, or large payloads.

**Independent Test**: Send 64KB message, verify received intact.

**Acceptance Scenarios**:

1. **Given** message of 10KB, **When** sent, **Then** automatically fragmented into ~8 packets
2. **Given** fragments arrive out of order, **When** all fragments received, **Then** message reassembled correctly
3. **Given** one fragment lost, **When** other fragments ACKed, **Then** only lost fragment retransmitted

---

### User Story 3 - Backward-Compatible API (Priority: P1)

As a developer, I want the existing `UdpReceiver` interface to work unchanged.

**Why this priority**: Minimize migration effort for existing code.

**Independent Test**: Existing unit tests pass without modification.

**Acceptance Scenarios**:

1. **Given** existing `packets` Flow subscription, **When** reliable layer active, **Then** complete messages emitted (not fragments)
2. **Given** existing `registerAppId()` call, **When** packet received, **Then** appId filtering still works
3. **Given** app-level payload format unchanged, **When** message delivered, **Then** original payload bytes preserved

---

### User Story 4 - Sender Delivery Confirmation (Priority: P2)

As a sender, I want to know when my message was delivered.

**Why this priority**: Enables application-level retry logic or user feedback.

**Independent Test**: Sender callback invoked within 100ms of receiver ACK.

**Acceptance Scenarios**:

1. **Given** message sent, **When** all fragments ACKed, **Then** delivery callback invoked
2. **Given** message sent, **When** max retries exceeded, **Then** failure callback invoked

---

### Edge Cases

- **Duplicate packets**: Receiver deduplicates by SeqNum
- **Very old ACKs**: Sender ignores ACKs for already-confirmed packets
- **Fragment timeout**: If not all fragments arrive within 30s, discard partial message
- **Sender restart**: Fresh SeqNum sequence; receiver may briefly hold stale fragment buffers

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement Selective Repeat ARQ with SACK
- **FR-002**: System MUST fragment messages >1400 bytes automatically
- **FR-003**: System MUST reassemble fragments in any arrival order
- **FR-004**: System MUST retransmit unacked packets after timeout (default 200ms)
- **FR-005**: System MUST fast-retransmit after 3 out-of-order ACKs
- **FR-006**: System MUST use strictly increasing packet sequence numbers
- **FR-007**: System MUST maintain the existing `UdpReceiver` interface contract
- **FR-008**: System MUST preserve existing packet format (appId prefix) in payload
- **FR-009**: System MUST provide delivery confirmation callback to senders
- **FR-010**: Python sender tool MUST implement the reliable protocol
- **FR-011**: Implementation MUST be a standalone Gradle module (`:reliable-udp`)
- **FR-012**: Borrowed code MUST include source URL, license reference, and modification notes

### Non-Functional Requirements

- **NFR-001**: Memory usage <10% increase over raw UDP
- **NFR-002**: Latency overhead <50ms on local network (RTT contribution from ACK)
- **NFR-003**: Compatible with Android API 29+ (no native libraries)
- **NFR-004**: Pure Kotlin implementation (no Netty, no JNI)

### Key Entities

- **ReliableSocket**: Wraps DatagramSocket with reliability layer
- **PacketBuffer**: Tracks sent packets awaiting ACK, with timestamps
- **AckQueue**: Batches received packet numbers for SACK generation
- **FragmentBuffer**: Reassembles message fragments by MsgID
- **ReliablePacket**: Header + payload with serialization

## Success Criteria *(mandatory)*

### Measurable Outcomes

Adapted from [QUIC Interop Test Cases](https://github.com/quic-interop/quic-interop-runner):

- **SC-001**: 100% message delivery at 30% packet loss (50 messages × 1KB)
- **SC-002**: 100% message delivery at 2% packet loss for 2MB transfer
- **SC-003**: Large message (64KB) delivered intact via fragmentation
- **SC-004**: Existing unit tests pass without modification
- **SC-005**: Delivery confirmation within 500ms on local network

## Reference Implementations

### Code to Borrow/Adapt

| Source | What to Use | License |
|--------|-------------|---------|
| [Quincy](https://github.com/protocol7/quincy) | PacketBuffer, AckQueue patterns | Apache 2.0 |
| [QUIC RFC 9002](https://www.rfc-editor.org/rfc/rfc9002.html) | Loss detection algorithms | IETF standard |
| [quic-interop-runner](https://github.com/quic-interop/quic-interop-runner) | Test case definitions | MIT |

### Test Infrastructure

From [quic-interop-runner testcases.py](https://github.com/quic-interop/quic-interop-runner):

| Test Case | Parameters | What It Validates |
|-----------|------------|-------------------|
| HandshakeLoss | 30% loss, 50 runs, 1KB each | Reliability under extreme loss |
| TransferLoss | 2% loss, 2MB file | Sustained transfer reliability |
| Multiconnect | 50 sequential sends | Connection-less operation |

## Assumptions

- Local network latency <50ms (LAN/WiFi use case)
- Maximum message size 64KB (reasonable for our use case)
- Receiver can buffer up to 100 pending fragments
- Kotlin coroutines sufficient for async packet handling (no Netty needed)

## Out of Scope

- Congestion control (local network assumption)
- Connection establishment/teardown (connectionless design)
- Encryption (handled at app layer)
- QUIC-compatible wire format (custom protocol for simplicity)
- Stream multiplexing (single logical stream per appId)
