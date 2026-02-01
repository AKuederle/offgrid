# Data Model: Message Persistence

**Date**: 2026-02-01
**Feature**: 002-message-persistence

## Entities

### PacketEntity (NEW)

Room entity for persistent storage of UDP packets. Stored in SQLite database that survives app lifecycle.

| Field | Type | SQLite Type | Description | Constraints |
|-------|------|-------------|-------------|-------------|
| `id` | `Long` | INTEGER | Unique identifier | Primary key, auto-generated |
| `appId` | `String` | TEXT | Application identifier for filtering | Non-null, indexed |
| `data` | `ByteArray` | BLOB | Packet payload (without appId prefix) | Non-null, max 65,500 bytes |
| `sourceIp` | `String` | TEXT | Sender IP address | Non-null, e.g., "192.168.1.100" |
| `sourcePort` | `Int` | INTEGER | Sender UDP port | Non-null, 1-65535 |
| `timestamp` | `Long` | INTEGER | Reception time (epoch millis) | Non-null, positive, indexed DESC |

**Kotlin Definition**:
```kotlin
@Entity(
    tableName = "packets",
    indices = [
        Index(value = ["appId"]),
        Index(value = ["timestamp"], orders = [Index.Order.DESC]),
        Index(value = ["appId", "timestamp"], orders = [Index.Order.ASC, Index.Order.DESC])
    ]
)
data class PacketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "appId")
    val appId: String,

    @ColumnInfo(name = "data")
    val data: ByteArray,

    @ColumnInfo(name = "sourceIp")
    val sourceIp: String,

    @ColumnInfo(name = "sourcePort")
    val sourcePort: Int,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
```

**Validation Rules**:
- `appId` must be non-empty string (1-255 bytes UTF-8)
- `data` must not exceed 65,500 bytes (UDP max minus prefix overhead)
- `sourceIp` must be valid IPv4 address format
- `sourcePort` must be valid port (1-65535)
- `timestamp` must be positive (milliseconds since epoch)

**Indexing Strategy**:
- Primary index on `id` (automatic)
- Secondary index on `appId` for app-specific filtering
- Secondary index on `timestamp DESC` for ORDER BY queries
- Composite index on `(appId, timestamp DESC)` for filtered+ordered queries
- NO index on `data` (BLOB indexing is expensive and not needed)

---

### Wire Format: AppId Prefix

UDP packets must include a length-prefixed appId at the start:

```
+--------+------------------+------------------+
| Length |      AppId       |     Payload      |
| 1 byte | Length bytes     | Remaining bytes  |
+--------+------------------+------------------+
```

- **Byte 0**: Length of appId (1-255)
- **Bytes 1..length**: AppId as UTF-8 string
- **Remaining bytes**: Actual payload data

**Examples**:
```
[04] [74 65 73 74] [48 65 6C 6C 6F]  = appId="test", payload="Hello"
[03] [61 70 70] [01 02 03]           = appId="app", payload=0x010203
```

**Parsing**: Use `PacketParser.parse(data)` to extract appId and payload.
**Encoding**: Use `PacketParser.encode(appId, payload)` for sending.

---

### UdpPacket (EXISTING - No Changes)

In-memory representation of received UDP packet. Defined in `udp-service/src/main/kotlin/com/example/udpservice/api/UdpPacket.kt`.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `data` | `ByteArray` | Raw packet payload | Non-null, max 65,535 bytes |
| `sourceAddress` | `InetSocketAddress` | Sender IP and port | Non-null |
| `timestamp` | `Long` | Reception time (epoch millis) | Non-null, positive |
| `displayText` | `String` | Human-readable representation | Derived property |

**Relationship to PacketEntity**:
- `UdpPacket` is received from socket and emitted via Flow
- `PacketEntity` is persisted version stored in Room database
- Conversion: `UdpPacket` → `PacketEntity` when persisting (after parsing appId prefix)

**Conversion Function**:
```kotlin
// Parse appId from packet data and convert to entity
fun UdpPacket.toEntity(): PacketEntity? {
    val parsed = PacketParser.parse(this.data) ?: return null
    return PacketEntity(
        appId = parsed.appId,
        data = parsed.payload,
        sourceIp = this.sourceAddress.hostString,
        sourcePort = this.sourceAddress.port,
        timestamp = this.timestamp
    )
}

// With pre-validated appId
fun UdpPacket.toEntity(appId: String, payload: ByteArray): PacketEntity = PacketEntity(
    appId = appId,
    data = payload,
    sourceIp = this.sourceAddress.hostString,
    sourcePort = this.sourceAddress.port,
    timestamp = this.timestamp
)
```

---

### ReceiverState (EXISTING - No Changes)

Sealed interface representing UDP receiver state machine. No changes needed for persistence feature.

```
Stopped  → Starting → Running(port, addresses)
                   → Error(message)
Running  → Stopped
Error    → Starting
```

---

## Database Schema

### Table: `packets`

```sql
CREATE TABLE packets (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    appId TEXT NOT NULL,
    data BLOB NOT NULL,
    sourceIp TEXT NOT NULL,
    sourcePort INTEGER NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE INDEX idx_packets_appId ON packets (appId);
CREATE INDEX idx_packets_timestamp ON packets (timestamp DESC);
CREATE INDEX idx_packets_appId_timestamp ON packets (appId ASC, timestamp DESC);
```

### Database: `packet_database.db`

- **Version**: 1
- **Schema Export**: Disabled for MVP (enable for production)
- **Migration Strategy**: Destructive for MVP (implement proper migrations for production)

---

## Data Flow

### Write Path (Service → Database)

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  UdpSocket   │────▶│  UdpPacket   │────▶│ PacketEntity │────▶│  PacketDao   │
│  (receive)   │     │  (in-memory) │     │  (entity)    │     │  (insert)    │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
        │                    │                    │                    │
        │                    │                    │                    ▼
        │                    │                    │           ┌──────────────┐
        │                    │                    └──────────▶│   SQLite     │
        │                    │                                │  (persist)   │
        │                    │                                └──────────────┘
        │                    │
        │                    ▼
        │            ┌──────────────┐
        │            │  SharedFlow  │──────▶ UI (real-time display)
        └───────────▶│  (packets)   │
                     └──────────────┘
```

### Read Path (Database → UI)

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   SQLite     │────▶│  PacketDao   │────▶│ Flow<List>   │────▶│ Compose UI   │
│   (query)    │     │  (observe)   │     │ (reactive)   │     │ (LazyColumn) │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
```

---

## Capacity & Performance

### Storage Estimates

| Scenario | Packets | Avg Size | Storage |
|----------|---------|----------|---------|
| Typical usage | 1,000 | 100 bytes | ~100 KB |
| Heavy usage | 10,000 | 1 KB | ~10 MB |
| Stress test | 10,000 | 64 KB | ~640 MB |

### Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Single insert | <50ms | Async, non-blocking |
| Batch insert (100) | <200ms | Room transaction |
| Load 100 packets | <100ms | Indexed query |
| Load 10,000 packets | <500ms | Paginated if needed |
| Delete all | <2s | Single DELETE statement |

### Index Analysis

| Query | Index Used | Complexity |
|-------|------------|------------|
| `WHERE appId = ?` | `idx_packets_appId` | O(log n) |
| `ORDER BY timestamp DESC` | `idx_packets_timestamp` | O(log n) |
| `WHERE appId = ? ORDER BY timestamp DESC` | `idx_packets_appId_timestamp` | O(log n) |
| `WHERE id = ?` | Primary key | O(1) |
| Full table scan | None | O(n) |

---

## Relationships

```
┌─────────────────────┐         ┌──────────────────┐
│ UdpReceiverService  │────────▶│  PacketDatabase  │
│  (writes packets)   │         │   (singleton)    │
└─────────────────────┘         └────────┬─────────┘
                                         │
                                         │ owns
                                         ▼
                                ┌──────────────────┐
                                │    PacketDao     │
                                │   (interface)    │
                                └────────┬─────────┘
                                         │
                                         │ queries
                                         ▼
                                ┌──────────────────┐
                                │  PacketEntity[]  │
                                │    (table)       │
                                └──────────────────┘
                                         ▲
                                         │
                                         │ observes
┌─────────────────────┐         ┌────────┴─────────┐
│    MainActivity     │────────▶│   Flow<List>     │
│  (reads packets)    │         │   (reactive)     │
└─────────────────────┘         └──────────────────┘
```

- **UdpReceiverService** → **PacketDatabase**: 1:1 (via singleton)
- **PacketDatabase** → **PacketDao**: 1:1 (generated implementation)
- **PacketDao** → **PacketEntity**: 1:many (table rows)
- **MainActivity** → **PacketDao.Flow**: 1:1 (observation)

---

## State Transitions

### Packet Lifecycle

```
[UDP Socket Receive]
        │
        ▼
┌───────────────┐
│  UdpPacket    │  (ephemeral, in-memory)
│  created      │
└───────┬───────┘
        │
        ├──────────────────┐
        │                  │
        ▼                  ▼
┌───────────────┐  ┌───────────────┐
│  SharedFlow   │  │ PacketEntity  │
│  (UI stream)  │  │  (persist)    │
└───────────────┘  └───────┬───────┘
                           │
                           ▼
                   ┌───────────────┐
                   │   SQLite      │  (durable)
                   │   stored      │
                   └───────────────┘
```

### Database Lifecycle

```
[App/Service Start]
        │
        ▼
┌───────────────┐
│  getInstance  │  (lazy initialization)
└───────┬───────┘
        │
        ▼
┌───────────────┐
│  DB Created   │  (first call only)
│  or Opened    │  (subsequent calls)
└───────┬───────┘
        │
        ▼
┌───────────────┐
│  Available    │  ◀───┐
│  for queries  │      │ (survives app kill)
└───────┬───────┘      │
        │              │
        ▼              │
[Process Death] ───────┘
```
