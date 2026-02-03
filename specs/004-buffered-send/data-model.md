# Data Model: Buffered Send with Persistent Retry

**Feature**: 004-buffered-send
**Date**: 2026-02-02

## Entities

### OutboundMessage

Represents a message queued for delivery to a peer.

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Auto-generated primary key |
| peerHost | String | Target peer IP address |
| peerPort | Int | Target peer port |
| payload | ByteArray | Message content to deliver |
| status | DeliveryStatus | Current delivery state |
| retryCount | Int | Number of retry attempts made |
| createdAt | Long | Timestamp when message was queued (epoch millis) |
| lastAttemptAt | Long? | Timestamp of last delivery attempt |
| deliveredAt | Long? | Timestamp when ACK received (null if not delivered) |

**Constraints**:
- payload.size <= 65536 (64KB max)
- retryCount >= 0
- createdAt > 0
- lastAttemptAt == null OR lastAttemptAt >= createdAt

**Indexes**:
- Primary: id
- Secondary: (peerHost, peerPort, status) for peer-based queries
- Secondary: (status, lastAttemptAt) for retry scheduling

### DeliveryStatus (Enum)

State machine for outbound message delivery.

| Value | Description |
|-------|-------------|
| PENDING | Queued, not yet attempted |
| SENDING | Currently transmitting |
| DELIVERED | Successfully acknowledged |
| RETRYING | Failed, scheduled for retry |
| WAITING | Exhausted retries, waiting for peer activity |

**State Transitions**:

```
PENDING ──────────→ SENDING (start transmission)
                        │
                        ├──→ DELIVERED (ACK received, terminal)
                        │
                        └──→ RETRYING (transmission failed)
                                │
                                ├──→ SENDING (retry attempt)
                                │
                                └──→ WAITING (max retries exceeded)
                                        │
                                        └──→ PENDING (peer activity detected)
```

### Peer (Runtime Entity)

In-memory tracking of peer activity. Not persisted.

| Field | Type | Description |
|-------|------|-------------|
| host | String | Peer IP address |
| port | Int | Peer port |
| lastSeenAt | Long | Timestamp of last received message |
| pendingCount | Int | Count of pending outbound messages |

**Key**: (host, port) tuple

### PresenceMessage (Protocol)

Lightweight presence announcement packet. Uses existing header format.

| Field | Type | Value |
|-------|------|-------|
| type | PacketType | PRESENCE (0x03) |
| messageId | Int | 0 |
| sequenceNumber | Long | 0 |
| fragmentIndex | Short | 1 |
| fragmentTotal | Short | 1 |
| payload | ByteArray | empty (0 bytes) |

## Relationships

```
┌─────────────────┐         ┌─────────────────┐
│ OutboundMessage │ ──*:1── │      Peer       │
│                 │         │   (runtime)     │
│ peerHost        │────────→│ host            │
│ peerPort        │────────→│ port            │
└─────────────────┘         └─────────────────┘
```

- One Peer can have many OutboundMessages
- Peer is identified by (host, port) tuple
- OutboundMessage references Peer via peerHost + peerPort fields

## Database Schema (Room)

### Table: outbound_messages

```sql
CREATE TABLE outbound_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    peer_host TEXT NOT NULL,
    peer_port INTEGER NOT NULL,
    payload BLOB NOT NULL,
    status TEXT NOT NULL,      -- DeliveryStatus enum name
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_attempt_at INTEGER,
    delivered_at INTEGER
);

CREATE INDEX idx_outbound_peer_status
    ON outbound_messages(peer_host, peer_port, status);

CREATE INDEX idx_outbound_retry_schedule
    ON outbound_messages(status, last_attempt_at);
```

### Migration from v1 to v2

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE outbound_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                peer_host TEXT NOT NULL,
                peer_port INTEGER NOT NULL,
                payload BLOB NOT NULL,
                status TEXT NOT NULL,
                retry_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                last_attempt_at INTEGER,
                delivered_at INTEGER
            )
        """)
        database.execSQL("""
            CREATE INDEX idx_outbound_peer_status
            ON outbound_messages(peer_host, peer_port, status)
        """)
        database.execSQL("""
            CREATE INDEX idx_outbound_retry_schedule
            ON outbound_messages(status, last_attempt_at)
        """)
    }
}
```

## Validation Rules

### OutboundMessage

| Rule | Condition | Error |
|------|-----------|-------|
| Payload size | payload.size <= 65536 | "Payload exceeds 64KB limit" |
| Peer host | peerHost.isNotBlank() | "Peer host is required" |
| Peer port | peerPort in 1..65535 | "Invalid peer port" |
| Retry count | retryCount >= 0 | "Retry count cannot be negative" |
| Timestamps | lastAttemptAt == null OR lastAttemptAt >= createdAt | "Invalid attempt timestamp" |

### Status Transitions

| From | To | Allowed |
|------|----|---------|
| PENDING | SENDING | Yes |
| PENDING | * (other) | No |
| SENDING | DELIVERED | Yes |
| SENDING | RETRYING | Yes |
| SENDING | * (other) | No |
| RETRYING | SENDING | Yes |
| RETRYING | WAITING | Yes |
| RETRYING | * (other) | No |
| WAITING | PENDING | Yes |
| WAITING | * (other) | No |
| DELIVERED | * (any) | No (terminal) |
