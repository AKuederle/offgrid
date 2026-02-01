# Feature Specification: Message Persistence

**Feature Branch**: `002-message-persistence`
**Created**: 2026-02-01
**Status**: Draft
**Input**: User description: "Message persistence with Room database for background packet retention - demonstrate messages are retained when app is backgrounded/closed"

## Overview

Enable the UDP service to persist received packets to local storage so that messages are retained when the main app is backgrounded or closed. When the user returns to the app, they can see all packets received while away. This proves the foreground service can reliably capture and store network traffic independently of the UI lifecycle.

## User Scenarios & Testing

### User Story 1 - Messages Persist When App Backgrounded (Priority: P1)

As a developer, I want received UDP packets to be saved to storage so that when I background the app and return later, I can see all packets that arrived while the app was not visible.

**Why this priority**: This is the core value proposition - proving messages are not lost when the UI is inactive. Without persistence, the service is useless for real-world scenarios where apps are frequently backgrounded.

**Independent Test**: Start service, send packets, press Home to background app, wait 30 seconds, send more packets, return to app - all packets should be visible.

**Acceptance Scenarios**:

1. **Given** the service is running and app is in foreground, **When** I send a UDP packet, **Then** the packet is stored and appears in the message log.
2. **Given** the service is running and app is backgrounded, **When** I send a UDP packet, **Then** the packet is stored even though UI is not visible.
3. **Given** packets were received while app was backgrounded, **When** I return to the app, **Then** I see all packets that arrived while I was away.
4. **Given** the service is running, **When** packets arrive rapidly (100/sec), **Then** all packets are stored without data loss.

---

### User Story 2 - Messages Persist After App Killed (Priority: P1)

As a developer, I want messages to remain in storage even if I swipe the app away from recents, so that the foreground service continues capturing packets independently of the app process.

**Why this priority**: Demonstrates true background operation - the service must survive app termination.

**Independent Test**: Start service, send packets, kill app from recents, send more packets, reopen app - all packets should be visible.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** I swipe the app away from recents, **Then** the foreground service continues running (notification visible).
2. **Given** the service is running and app was killed, **When** UDP packets are sent, **Then** they are stored in the database.
3. **Given** packets were received after app was killed, **When** I reopen the app, **Then** I see all packets received since I killed it.

---

### User Story 3 - View Message History (Priority: P2)

As a developer, I want to scroll through the history of all received packets so that I can review messages that arrived over time.

**Why this priority**: Secondary to persistence but essential for demonstrating the feature works.

**Independent Test**: Send 200 packets, scroll through the list, verify all are accessible.

**Acceptance Scenarios**:

1. **Given** 100+ packets have been received, **When** I open the message log, **Then** I can scroll to see all packets (newest first).
2. **Given** I am viewing the message log, **When** a new packet arrives, **Then** it appears at the top without disrupting my scroll position.
3. **Given** I am viewing the message log, **When** I tap on a packet, **Then** I can see full details (complete payload, timestamp, source).

---

### User Story 4 - Clear Message History (Priority: P3)

As a developer, I want to clear all stored messages so that I can start fresh for new testing sessions.

**Why this priority**: Nice-to-have for testing workflows but not critical for proving persistence works.

**Independent Test**: With messages stored, tap "Clear All", confirm empty log, verify new messages still work.

**Acceptance Scenarios**:

1. **Given** messages exist in storage, **When** I tap "Clear All", **Then** all messages are deleted and the log shows empty.
2. **Given** I cleared messages, **When** new packets arrive, **Then** they are stored normally.

---

### Edge Cases

- What happens when storage is nearly full (device low on space)?
- How does the system handle corrupted database records?
- What happens if a packet arrives during database migration?
- How are very large packets (near 64KB UDP max) handled in storage?
- What happens if two packets arrive with identical timestamps?

## Requirements

### Functional Requirements

#### Storage

- **FR-001**: Service MUST persist received UDP packets to local device storage that survives app lifecycle.
- **FR-002**: Storage MUST retain packets across app backgrounding, app kill (swipe from recents), and app restart.
- **FR-003**: Storage MUST handle at least 10,000 packets without performance degradation.
- **FR-004**: Each stored packet MUST include: raw payload, source IP, source port, and receive timestamp.
- **FR-005**: Storage operations MUST NOT block packet reception (async writes).

#### Message Log UI

- **FR-006**: App MUST display stored packets in a scrollable list, newest first.
- **FR-007**: App MUST load packet history from storage when opened (not just in-memory).
- **FR-008**: App MUST update the list when new packets arrive without losing scroll position.
- **FR-009**: App MUST provide ability to view full packet details (complete payload, not truncated).
- **FR-010**: App MUST provide ability to clear all stored messages.

#### Service Integration

- **FR-011**: Foreground service MUST write packets to storage even when app UI is not running.
- **FR-012**: Service MUST handle storage errors gracefully (log error, continue receiving).
- **FR-013**: Service MUST survive app process termination (continues via foreground service).

### Key Entities

- **Stored Packet**: A received UDP packet persisted to storage with: unique ID, raw payload bytes, source address (IP:port), receive timestamp (millisecond precision).
- **Message History**: The complete collection of stored packets, ordered by receive time.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of packets sent while app is backgrounded are visible when app returns to foreground.
- **SC-002**: 100% of packets sent while app is killed (swiped from recents) are visible when app is reopened.
- **SC-003**: App can store and display 10,000 packets without noticeable lag (<500ms to load history).
- **SC-004**: Packet writes complete within 50ms average (measured via logs), not blocking reception.
- **SC-005**: Service remains running (notification visible) for at least 1 hour with app backgrounded.
- **SC-006**: Clear operation removes all packets and frees storage within 2 seconds.

## Out of Scope

The following are explicitly NOT part of this feature:

- Message retention policies (auto-delete old messages)
- Message search or filtering
- Export/share functionality
- Cloud backup or sync
- Topic-based routing or message categorization
- Cross-app message sharing (ContentProvider access)
- Sending UDP packets from Android

## Assumptions

- Device has sufficient storage space for message database (at least 100MB free).
- Packets are stored as-is without transformation or parsing.
- Retention is indefinite until user clears manually (no auto-expiration).
- Performance target is single-device, single-user scenario (no multi-device sync).
- UTF-8 display for text payloads; binary shown as hex.
