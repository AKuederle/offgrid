# Feature Specification: Reliable Transport Layer

**Feature Branch**: `003-reliable-transport`
**Created**: 2026-02-01
**Status**: Draft
**Input**: User description: "Switch from raw UDP to Kotlin reliable-udp library for automatic retry and reliability features"

## Overview

Replace the current raw UDP implementation (`DatagramSocket`) with the [seniorjoinu/reliable-udp](https://github.com/seniorjoinu/reliable-udp) Kotlin library. This library provides reliable packet delivery using fountain codes, eliminating packet loss without traditional retry mechanisms.

### Why UDP (Not TCP)?

The system intentionally uses UDP rather than TCP or other connection-based protocols:

- **Connectionless**: No handshake required; devices can send messages immediately without establishing a session
- **No TLS/certificates**: Security is handled at the application layer with app-level encryption of payload data
- **Simpler network topology**: No connection state to maintain across NAT or network changes
- **Future multicast potential**: UDP enables one-to-many broadcasting if needed

### Why Reliable Transport?

Current limitations with raw UDP:
- **No delivery guarantee**: Packets can be lost without notification
- **No ordering**: Packets may arrive out of order
- **No congestion control**: Can overwhelm the network or receiver
- **Manual retry logic**: Would need to implement ACK/retry ourselves

Benefits of reliable-udp library (while preserving UDP's connectionless nature):
- **Fountain codes**: Mathematical approach to reliability without retransmission
- **Coroutine-native**: Suspending `send()` and `receive()` functions
- **Thread-safe multiplexing**: Built-in support for concurrent operations
- **ACK-based confirmation**: Sender knows when data is reconstructed
- **Configurable**: MTU, window size, congestion timeout, cleanup intervals

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Guaranteed Message Delivery (Priority: P1)

As a sender, I want my packets to be reliably delivered even on lossy networks, so that I don't lose critical data.

**Why this priority**: Core value proposition - without reliable delivery, the library migration provides no benefit.

**Independent Test**: Send 100 packets over a simulated lossy network (10% packet loss) and verify all 100 are received.

**Acceptance Scenarios**:

1. **Given** sender sends a packet, **When** network drops some UDP datagrams, **Then** receiver still reconstructs complete message via fountain codes
2. **Given** sender sends a large message (>MTU), **When** message is fragmented, **Then** receiver reconstructs the complete message
3. **Given** sender sends a packet, **When** delivery is confirmed, **Then** sender receives ACK callback

---

### User Story 2 - Backward-Compatible API (Priority: P1)

As a developer using the udp-service library, I want the API to remain largely unchanged, so that I don't need to rewrite my integration code.

**Why this priority**: Breaking API changes would require updating all consumers, increasing migration friction.

**Independent Test**: Existing `UdpReceiver` interface and packet flow continue to work without changes to consuming code.

**Acceptance Scenarios**:

1. **Given** existing `UdpReceiver.packets` Flow subscription, **When** reliable transport is enabled, **Then** packets are still emitted to the same Flow
2. **Given** existing `registerAppId()` API, **When** using reliable transport, **Then** appId filtering continues to work
3. **Given** existing packet format (1-byte appId length prefix), **When** using reliable transport, **Then** packet parsing remains compatible

---

### User Story 3 - Sender Confirmation (Priority: P2)

As a sender (Python tool), I want to know when my message was successfully received, so that I can implement application-level acknowledgment.

**Why this priority**: Enables senders to confirm delivery, but the receiver-side reliability is more critical.

**Independent Test**: Python sender tool receives ACK after sending a packet.

**Acceptance Scenarios**:

1. **Given** sender sends a packet with reliable transport, **When** receiver reconstructs message, **Then** sender receives ACK
2. **Given** sender sends a packet, **When** no ACK received within timeout, **Then** sender can detect delivery failure

---

### User Story 4 - Configuration Options (Priority: P3)

As an advanced user, I want to configure reliability parameters, so that I can tune performance for my network conditions.

**Why this priority**: Nice-to-have customization, but sensible defaults should work for most cases.

**Independent Test**: Change MTU setting and verify it affects packet fragmentation behavior.

**Acceptance Scenarios**:

1. **Given** default configuration, **When** service starts, **Then** reasonable defaults are used (MTU ~1400, appropriate timeouts)
2. **Given** custom configuration, **When** service starts with overrides, **Then** custom values are applied

---

### Edge Cases

- What happens when the reliable-udp library fails to initialize? → Fall back to error state, do not silently use raw UDP
- How does the system handle extremely large messages (>64KB)? → Library should fragment and reassemble automatically
- What happens if the sender uses raw UDP but receiver expects reliable? → Packets should be dropped with clear logging (protocol mismatch)
- How does the system handle rapid sender restart? → Multiplexing should handle connection state cleanup

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST replace `DatagramSocket` with `reliable-udp` library's socket implementation
- **FR-002**: System MUST maintain the existing `UdpReceiver` interface contract (packets Flow, state Flow, registerAppId)
- **FR-003**: System MUST support the existing packet format (1-byte appId length prefix + payload)
- **FR-004**: System MUST provide ACK confirmation to senders when packets are successfully received
- **FR-005**: System MUST handle packet fragmentation and reassembly transparently for messages larger than MTU
- **FR-006**: System MUST log reliability events (ACK sent, packet reconstructed, errors) for debugging
- **FR-007**: System MUST gracefully handle library initialization failures with clear error messages
- **FR-008**: Python sender tool MUST be updated to use reliable-udp wire protocol

### Non-Functional Requirements

- **NFR-001**: Reliable transport MUST NOT significantly increase memory usage (target: <10% increase)
- **NFR-002**: Reliable transport SHOULD improve effective throughput on lossy networks compared to raw UDP with manual retry
- **NFR-003**: Library integration MUST be compatible with Android API 29+ (minSdk)

### Key Entities

- **ReliableSocket**: Wrapper around reliable-udp library socket, implementing same lifecycle as current UdpSocket
- **ReliablePacket**: Internal representation during reconstruction (managed by library)
- **AckCallback**: Mechanism for notifying senders of successful delivery

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% packet delivery on networks with up to 20% simulated packet loss (compared to ~80% with raw UDP)
- **SC-002**: Existing unit tests and integration tests pass without modification (API compatibility)
- **SC-003**: ACK received by sender within 500ms of packet delivery on local network
- **SC-004**: Service starts successfully with reliable transport in under 2 seconds

## Library Alternatives Considered

| Library | Decision | Rationale |
|---------|----------|-----------|
| [seniorjoinu/reliable-udp](https://github.com/seniorjoinu/reliable-udp) | **Selected** | Kotlin coroutine-native, connectionless, fountain codes for reliability |
| [java-Kcp](https://github.com/l42111996/java-Kcp) | Rejected | Java/Netty overhead, KCP has connection concept |
| [rozsa-network](https://github.com/dendriel/rozsa-network) | Rejected | Java-only, connection-based RUDP |
| [RSocket-Kotlin](https://github.com/rsocket/rsocket-kotlin) | Rejected | TCP/WebSocket based, requires connections |
| [KryoNet](https://github.com/EsotericSoftware/kryonet) | Rejected | UDP is unreliable in KryoNet, only TCP is reliable |

**Fallback plan**: If `seniorjoinu/reliable-udp` proves incompatible with modern Android/Kotlin, evaluate `java-Kcp` or implement a simpler ARQ layer ourselves.

## Assumptions

- The `seniorjoinu/reliable-udp` library is compatible with Android and Kotlin 1.9.x
- The library's fountain codes approach provides sufficient reliability for local network use cases
- The library is actively maintained or stable enough for production use (last update: 2019, but stable)
- Both sender and receiver must use the reliable-udp protocol (not interoperable with raw UDP)

## Out of Scope

- TCP fallback option
- Custom fountain code implementation
- Cross-network (internet) reliability guarantees
- Backward compatibility with raw UDP senders (will require Python tool update)
