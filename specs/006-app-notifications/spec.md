# Feature Specification: App Prefix Notifications with Deep Linking

**Feature Branch**: `006-app-notifications`
**Created**: 2026-02-02
**Status**: Draft
**Input**: User description: "Implement configurable notifications for inbound UDP messages with app prefix registration, unread count display, and deep linking to specific app views"

## Architecture Overview

The system follows a **broker-broadcast architecture** where the broker stores messages and notifies client apps via explicit broadcasts. Client apps handle their own notifications with full context (decryption, formatting, etc.).

### Message Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      BROKER APP                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  UDP Foreground Service                              │   │
│  │  1. Receives UDP message                             │   │
│  │  2. Persists to database (always)                    │   │
│  │  3. Sends explicit broadcast to registered package   │   │
│  │     intent.setPackage(registration.packageName)      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │
         │ Explicit Broadcast (minimal ping)
         │ Action: "com.example.udpservice.NEW_MESSAGE"
         │ Extras: { prefix: "alerts" }
         │ setPackage("com.clientapp.package")
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                      CLIENT APP                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BroadcastReceiver                                   │   │
│  │  - Receives "new message" ping                       │   │
│  └─────────────────────────────────────────────────────┘   │
│         │                                                   │
│         ├── IF foreground ──▶ Pull immediately from broker  │
│         │                     UI updates, mark as read      │
│         │                                                   │
│         └── IF backgrounded ▶ Enqueue WorkManager           │
│                               Pull from broker              │
│                               Decrypt (if needed)           │
│                               Show notification             │
└─────────────────────────────────────────────────────────────┘
```

### This Feature's Scope (Test Implementation)

For testing, the broker app broadcasts to itself and handles notifications internally:

```
┌─────────────────────────────────────────────────────────────┐
│                      BROKER APP                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  UDP Foreground Service                              │   │
│  │  - Stores messages                                   │   │
│  │  - Broadcasts to self: setPackage(context.packageName)│  │
│  └─────────────────────────────────────────────────────┘   │
│         │                                                   │
│         ▼                                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BroadcastReceiver (same app)                        │   │
│  │  - Foreground: immediate pull, UI update             │   │
│  │  - Background: WorkManager → pull → notification     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  Test prefixes: "broker", "alerts"                          │
│  Two separate message pages                                 │
└─────────────────────────────────────────────────────────────┘
```

## Clarifications

### Session 2026-02-02

- Q: What IPC mechanism should client apps use to register with the broker app? → A: AIDL Bound Service (future feature, not this scope)
- Q: Is AIDL cross-app registration in scope for this feature? → A: No, this feature builds the infrastructure; AIDL exposure is a future feature
- Q: Should broker create notifications directly? → A: No, broker broadcasts to client apps; clients handle their own notifications (supports decryption, context)
- Q: Should broadcast include message payload? → A: No, minimal ping only (prefix); client pulls messages from broker's database

## Scope

### In Scope (This Feature)

- Broker: Store messages and broadcast explicit ping to registered package
- Client: BroadcastReceiver to handle message arrival ping
- Client: Pull messages from broker's database
- Client: Show notifications with unread count and deep links
- Client: Mark messages as read when viewing
- Per-prefix notification channels
- Internal registration API (within broker app)
- Broker app registers two test prefixes ("broker", "alerts") pointing to itself
- Two separate message pages in broker app UI

### Out of Scope (Future Features)

- AIDL bound service for cross-app registration
- Security model for cross-app registration (signature permissions, allowlists)
- External client apps registering prefixes
- Message encryption/decryption (client apps will handle this)
- ContentProvider for cross-app data access (direct DB for now)

### Design Constraints (Future-Proofing)

The architecture MUST be designed so that:
- Adding AIDL registration later requires no broadcast mechanism changes
- External client apps can receive the same broadcasts
- Client apps can implement their own notification logic (decryption, formatting)
- Registration records store package name for explicit broadcast targeting

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive Broadcast on Message Arrival (Priority: P1)

As a client app with a registered prefix, when a UDP message arrives for my prefix, the broker broadcasts a ping to my package so I can pull and process the message.

**Why this priority**: This is the foundation - client apps must know when messages arrive.

**Independent Test**: Can be fully tested by sending a UDP message and verifying the BroadcastReceiver fires with correct prefix.

**Acceptance Scenarios**:

1. **Given** prefix "broker" is registered with package "com.example.udpbroker", **When** a UDP message arrives for "broker", **Then** an explicit broadcast is sent to "com.example.udpbroker" with extra prefix="broker"
2. **Given** multiple prefixes are registered to different packages, **When** messages arrive, **Then** each package receives only broadcasts for its registered prefixes
3. **Given** a prefix is registered but notifications disabled, **When** a message arrives, **Then** broadcast is still sent (client decides whether to notify)

---

### User Story 2 - Show Notification When Backgrounded (Priority: P1)

As a user with the app backgrounded, when a message arrives for my prefix, I receive a system notification showing the unread count so I know there's activity.

**Why this priority**: This is the core user value - awareness of new messages without watching the app.

**Independent Test**: Can be fully tested by backgrounding the app, sending a UDP message, and verifying a notification appears.

**Acceptance Scenarios**:

1. **Given** the app is backgrounded, **When** a message arrives, **Then** WorkManager processes it and shows a notification with unread count
2. **Given** a notification exists with "3 new messages", **When** another message arrives, **Then** the notification updates to "4 new messages"
3. **Given** notifications are disabled for a prefix, **When** a message arrives, **Then** no notification is shown (but message is still stored)

---

### User Story 3 - Pull Messages Immediately When Foreground (Priority: P1)

As a user with the app in foreground viewing messages, when a new message arrives, the UI updates immediately without showing a notification.

**Why this priority**: Real-time updates when actively using the app.

**Independent Test**: Can be fully tested by viewing the messages screen, sending a UDP message, and verifying the UI updates immediately.

**Acceptance Scenarios**:

1. **Given** the app is showing the "broker" messages page, **When** a message arrives for "broker", **Then** it appears in the list immediately
2. **Given** the app is in foreground but on a different screen, **When** a message arrives, **Then** it may trigger a notification (unread count > 0)
3. **Given** the app is viewing messages, **When** new messages arrive, **Then** they are automatically marked as read

---

### User Story 4 - Open App View via Notification Deep Link (Priority: P1)

As a user who receives a notification, when I tap on it, the app opens directly to the view specified by its deep link URI.

**Why this priority**: Notifications must lead to relevant content.

**Independent Test**: Can be fully tested by tapping a notification and verifying navigation.

**Acceptance Scenarios**:

1. **Given** a notification for prefix "broker" with deep link `udptest://messages/broker`, **When** I tap it, **Then** the app opens to the broker messages page
2. **Given** notifications for both prefixes, **When** I tap "alerts" notification, **Then** I navigate to alerts page (not broker)
3. **Given** the app is already open, **When** I tap a notification, **Then** the app navigates to the correct page

---

### User Story 5 - Dual Prefix Test Configuration (Priority: P2)

As a tester, the broker app registers two prefixes pointing to itself and displays their messages on separate pages.

**Why this priority**: Validates the broadcast architecture works correctly.

**Independent Test**: Can be fully tested by sending messages to each prefix and verifying separation.

**Acceptance Scenarios**:

1. **Given** the broker app starts, **When** it initializes, **Then** it registers "broker" and "alerts" with its own package name
2. **Given** the app has two message pages, **When** I navigate between them, **Then** I see only messages for that prefix
3. **Given** messages for both prefixes, **When** backgrounded, **Then** I get separate notifications for each prefix

---

### Edge Cases

- How does the system handle rapid message bursts? Broadcasts are sent for each message; client batches notification updates.
- What happens if the client app's BroadcastReceiver is slow? WorkManager handles queuing; messages are safe in broker's DB.
- What if client app is force-stopped? Broadcast fails silently; messages remain in broker's DB for later retrieval.
- How are notifications handled when viewing messages? Messages marked as read → unread count drops → notification auto-dismisses.

## Requirements *(mandatory)*

### Functional Requirements

**Broker Side (UDP Service)**:
- **FR-001**: Broker MUST persist all received messages to database before broadcasting
- **FR-002**: Broker MUST send explicit broadcast (setPackage) to registered package when message arrives
- **FR-003**: Broadcast MUST be minimal: action + prefix extra only (no payload)
- **FR-004**: Registration MUST include: prefix, package name, notifications enabled flag, deep link URI

**Client Side (App)**:
- **FR-005**: Client MUST implement BroadcastReceiver for "com.example.udpservice.NEW_MESSAGE" action
- **FR-006**: When backgrounded, client MUST use WorkManager to process messages and show notifications
- **FR-007**: When foreground and viewing messages, client MUST pull immediately and update UI
- **FR-008**: Notification MUST show unread count for the specific prefix
- **FR-009**: Notification MUST use deep link URI from registration for tap action
- **FR-010**: Client MUST track read/unread status; notification dismisses when unread == 0
- **FR-011**: Each prefix MUST have its own notification channel

**Test App**:
- **FR-012**: Broker app MUST register "broker" and "alerts" prefixes with its own package name
- **FR-013**: Broker app MUST declare intent filter for `udptest://` URI scheme
- **FR-014**: Broker app MUST provide two separate message pages with navigation

### Key Entities

- **AppPrefixRegistration**: prefix, packageName, notificationsEnabled, deepLinkUri, createdAt
- **Message (PacketEntity)**: id, appId, data, sourceIp, sourcePort, timestamp, isRead
- **UnreadCount**: Derived from COUNT(*) WHERE isRead = false

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Broadcasts are sent within 100ms of message persistence
- **SC-002**: Notifications appear within 2 seconds of broadcast (backgrounded)
- **SC-003**: UI updates within 500ms of broadcast (foreground)
- **SC-004**: 100% of notification taps navigate to correct page
- **SC-005**: Unread counts are always accurate (zero drift)
- **SC-006**: System handles 100+ messages/minute without delays >5 seconds

## Assumptions

- Broker stores messages; client apps pull from broker's database
- For this feature, direct database access (same app); future: ContentProvider or AIDL
- Explicit broadcasts work reliably for same-signature apps
- WorkManager handles background processing within Android's constraints
- Deep link URIs follow standard Android URI format
- Notification lifecycle driven by unread count (no explicit foreground detection)

## Future Considerations

- **Cross-App Database Access**: ContentProvider or AIDL for external client apps to query messages
- **Message Encryption**: Client apps may decrypt messages before displaying
- **AIDL Registration**: External apps register via bound service
- **Broadcast Security**: Signature-level permission for broadcast receiver
