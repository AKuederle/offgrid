# Feature Specification: Buffered Send with Persistent Retry

**Feature Branch**: `004-buffered-send`
**Created**: 2026-02-02
**Status**: Draft
**Input**: User description: "Next we need a send functionality for the app. Sending should always be buffered. This means we always store the message in the db first then tell the layer to send it. This way the message will be reliably send even if the peer suddenly goes offline or our app gets put in the background. I would like to add a system that we try an exponential backoff resend when the peer is not responding (for maybe a minute). Then we stop, but try again to send undelivered messages as soon as we get any message from them"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send Message with Guaranteed Persistence (Priority: P1)

As a user, I want my outbound messages to be stored locally before transmission so that they survive app restarts, background states, and network interruptions. When I send a message, it should be immediately persisted and then transmitted to the peer.

**Why this priority**: This is the foundation of the entire feature. Without persistent storage before send, none of the retry or recovery mechanisms can work. This delivers immediate value by ensuring no message is ever lost due to app lifecycle events.

**Independent Test**: Can be fully tested by sending a message, force-closing the app before delivery, reopening the app, and observing the message is still queued for delivery.

**Acceptance Scenarios**:

1. **Given** the app is running and connected to a peer, **When** the user sends a message, **Then** the message is stored in the database before any transmission attempt
2. **Given** a message has been sent, **When** the app is force-closed during transmission, **Then** the message persists in storage and is available for retry on next app launch
3. **Given** the app is in the background, **When** the system reclaims memory, **Then** all pending messages remain in persistent storage

---

### User Story 2 - Automatic Retry with Exponential Backoff (Priority: P2)

As a user, I want the system to automatically retry failed message deliveries using an exponential backoff strategy so that temporary network issues don't result in lost messages, while also not overwhelming a struggling connection.

**Why this priority**: Once messages are persisted (P1), automatic retry is essential for reliability. Without retry, users would need to manually resend failed messages. The backoff prevents network flooding.

**Independent Test**: Can be tested by sending a message to an unreachable peer and observing retry attempts at increasing intervals over approximately one minute.

**Acceptance Scenarios**:

1. **Given** a message transmission fails, **When** the retry system activates, **Then** it retries with exponential backoff (starting at 1 second, doubling each attempt)
2. **Given** retry attempts are ongoing, **When** approximately one minute of total retry time elapses, **Then** the system pauses retry attempts and marks the message as "pending delivery"
3. **Given** a retry succeeds, **When** acknowledgment is received, **Then** the message is marked as "delivered" and no further retries occur

---

### User Story 3 - Resume Delivery on Peer Activity (Priority: P3)

As a user, I want undelivered messages to automatically resume delivery attempts when the peer shows signs of being online again, so I don't have to manually trigger resends.

**Why this priority**: This provides a seamless recovery mechanism after the retry timeout. It leverages incoming messages as a "peer is alive" signal, which is a natural trigger for resuming delivery without user intervention.

**Independent Test**: Can be tested by having a message fail all retries, then receiving an incoming message from the same peer and observing automatic resumption of delivery attempts.

**Acceptance Scenarios**:

1. **Given** a message has exhausted its retry attempts, **When** any message is received from the target peer, **Then** the system immediately resumes delivery attempts for all pending messages to that peer
2. **Given** multiple messages are pending for a peer, **When** peer activity is detected, **Then** all pending messages are queued for delivery in order
3. **Given** the app is restarted, **When** the app binds to the service and receives a message from a peer, **Then** any stored pending messages to that peer are queued for delivery

---

### User Story 4 - Message Delivery Status Visibility (Priority: P4)

As a user, I want to see the delivery status of my outbound messages so I know whether my message was delivered, is being retried, or is waiting for the peer to come online.

**Why this priority**: While not essential for delivery functionality, status visibility gives users confidence and reduces anxiety about whether their messages are being sent.

**Independent Test**: Can be tested by sending messages under various conditions (successful delivery, retry in progress, pending) and observing the correct status displayed.

**Acceptance Scenarios**:

1. **Given** a message is successfully delivered, **When** viewing the message list, **Then** the message shows "delivered" status
2. **Given** a message is being retried, **When** viewing the message list, **Then** the message shows "sending" or "retrying" status with retry count
3. **Given** a message has exhausted retries, **When** viewing the message list, **Then** the message shows "pending - waiting for peer" status

---

### User Story 5 - Presence Broadcast for Peer Discovery (Priority: P5)

As a user, I want to broadcast a "presence" signal to announce that my device is online, so that peers with pending messages for me can immediately start delivering them.

**Why this priority**: This is an optimization that improves delivery speed when a device comes back online. Without it, the device must wait to receive a message attempt from a peer before delivery resumes. With presence broadcast, peers proactively learn the device is available.

**Independent Test**: Can be tested by having Peer A with pending messages for Peer B, then Peer B sends a presence broadcast, and observing that Peer A immediately resumes delivery.

**Acceptance Scenarios**:

1. **Given** the foreground service starts or network connectivity is restored, **When** the system sends a presence broadcast, **Then** a lightweight "I'm here" message is sent via UDP broadcast to the local network
2. **Given** a peer receives a presence broadcast, **When** there are pending messages for that peer, **Then** the system immediately resumes delivery attempts
3. **Given** a presence broadcast is received, **When** there are no pending messages for that peer, **Then** the broadcast is acknowledged but no action is taken

---

### User Story 6 - Kotlin CLI Tool Replaces Python Tool (Priority: P6)

As a developer, I want a Kotlin-based CLI tool that wraps the reliable-udp library so that I have a single source of truth for the protocol implementation and don't need to maintain duplicate send/receive logic in Python.

**Why this priority**: This is a developer tooling concern, not a user-facing feature. However, it eliminates code duplication and ensures the test tool always uses the same protocol implementation as the library.

**Independent Test**: Can be tested by using the Kotlin CLI to send messages to the Android app and verifying they are received correctly.

**Acceptance Scenarios**:

1. **Given** the Kotlin CLI is installed, **When** I run a send command, **Then** the message is sent using the reliable-udp library directly
2. **Given** the Kotlin CLI is running in receive mode, **When** a message arrives, **Then** it is displayed using the same parsing logic as the library
3. **Given** the Python tool exists, **When** this feature is complete, **Then** the Python tool is deprecated and removed from the repository

---

### Edge Cases

- What happens when the device has no network connectivity at all? Messages are stored and marked as pending, retries begin when connectivity returns.
- How does the system handle sending to multiple peers simultaneously? Each peer has independent retry state and backoff timers.
- What happens if the user sends the same message content multiple times? Each send creates a distinct message entry with its own delivery lifecycle.
- What happens when storage is full? The system should fail gracefully and inform the user that the message cannot be queued.
- What is the maximum number of pending messages per peer? Reasonable defaults apply (e.g., 1000 messages per peer) to prevent unbounded growth.
- What happens if presence broadcast is not supported on the network? System falls back to unicast presence or waits for regular message activity.
- How often can presence broadcasts be sent? Rate-limited to prevent broadcast storms (e.g., max once per 30 seconds).
- What happens if multiple devices broadcast presence simultaneously? Each device handles incoming broadcasts independently.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist outbound messages to local storage before attempting transmission
- **FR-002**: System MUST track delivery status for each outbound message (pending, sending, delivered, failed-retrying, failed-waiting)
- **FR-003**: System MUST automatically retry failed transmissions using exponential backoff starting at 1 second
- **FR-004**: System MUST pause retry attempts after approximately 60 seconds of cumulative retry time
- **FR-005**: System MUST resume delivery attempts for pending messages when any inbound message is received from the target peer
- **FR-006**: System MUST deliver pending messages in the order they were originally sent (FIFO per peer)
- **FR-007**: System MUST update message status immediately when delivery is confirmed via acknowledgment
- **FR-008**: System MUST restore pending message queue from storage on app restart
- **FR-009**: System MUST provide a way for users to view the delivery status of outbound messages
- **FR-010**: System MUST allow users to manually retry a pending message or cancel it
- **FR-011**: System MUST support a "presence" message type with no payload content
- **FR-012**: System MUST be able to send presence messages via UDP broadcast to the local network
- **FR-013**: System MUST send a presence broadcast when the foreground service starts or network connectivity is restored
- **FR-014**: System MUST treat received presence broadcasts the same as any other peer activity (triggering delivery resume)
- **FR-015**: A Kotlin CLI tool MUST be provided that wraps the reliable-udp library for testing
- **FR-016**: The Kotlin CLI MUST support sending messages to a specified host and port
- **FR-017**: The Kotlin CLI MUST support receiving messages and displaying them
- **FR-018**: The Kotlin CLI MUST support sending presence broadcasts
- **FR-019**: The Python udp-sender tool MUST be removed from the repository

### Key Entities

- **OutboundMessage**: Represents a message queued for delivery. Contains: content, destination peer, creation timestamp, current status, retry count, last attempt timestamp
- **DeliveryStatus**: State of an outbound message: Pending (not yet attempted), Sending (transmission in progress), Delivered (ACK received), Retrying (failed, will retry), Waiting (exhausted retries, awaiting peer activity)
- **Peer**: Target destination for messages. Identified by address. Tracks: last seen timestamp, pending message count
- **PresenceMessage**: A lightweight protocol message with no payload, used to announce device availability. Can be unicast or broadcast.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of outbound messages survive app force-close and restart without data loss
- **SC-002**: Messages to responsive peers are delivered within 3 seconds under normal conditions
- **SC-003**: Failed messages retry with backoff: 1s, 2s, 4s, 8s, 16s, 32s (approximately 63 seconds total)
- **SC-004**: Delivery resumes within 1 second of receiving any message from a peer with pending outbound messages
- **SC-005**: Users can view accurate delivery status for all outbound messages
- **SC-006**: System handles at least 100 pending messages per peer without performance degradation
- **SC-007**: Presence broadcast reaches all peers on the local network within 1 second of foreground service start or connectivity restore
- **SC-008**: Kotlin CLI tool can send and receive messages using the same protocol as the Android app

## Assumptions

- The existing reliable-udp transport layer handles packet-level reliability (fragmentation, ACK, retransmission). This feature adds application-level persistence and retry for message delivery across longer timeframes and app lifecycle events.
- Peers are identified by their network address (IP:port). No additional peer identity mechanism is required.
- The Android Room database from feature 002 will be extended to store outbound messages.
- The existing UDP service architecture will be extended rather than replaced.
- "Approximately one minute" of retries means total retry time of 60-65 seconds, achieved via exponential backoff.
- The Python udp-sender tool will be fully replaced by a Kotlin CLI that directly uses the reliable-udp library, eliminating protocol duplication.
