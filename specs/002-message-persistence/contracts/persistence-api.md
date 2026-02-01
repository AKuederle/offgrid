# Internal API Contract: Persistence Layer

**Feature**: 002-message-persistence
**Date**: 2026-02-01
**Module**: `udp-service`

## Overview

This document defines the internal API contract for the persistence layer. These are Kotlin interfaces and Room components, not external REST APIs.

---

## PacketDao Interface

Room Data Access Object for packet persistence.

**Location**: `udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketDao.kt`

### Methods

#### `insertPacket`

Insert a single packet into the database.

```kotlin
@Insert
suspend fun insertPacket(packet: PacketEntity): Long
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `packet` | `PacketEntity` | Packet to insert |
| **Returns** | `Long` | Auto-generated row ID |

**Behavior**:
- Executes on IO dispatcher (Room default)
- Returns immediately with row ID
- Throws on constraint violation (rare)

**Usage**:
```kotlin
val id = packetDao.insertPacket(entity)
Log.d(TAG, "Inserted packet with id=$id")
```

---

#### `insertPackets`

Batch insert multiple packets (more efficient than individual inserts).

```kotlin
@Insert
suspend fun insertPackets(packets: List<PacketEntity>)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `packets` | `List<PacketEntity>` | Packets to insert |

**Behavior**:
- Room automatically wraps in transaction
- Atomic: all succeed or all fail
- More efficient than N individual inserts

**Usage**:
```kotlin
val batch = receivedPackets.map { it.toEntity() }
packetDao.insertPackets(batch)
```

---

#### `observePackets`

Observe packets as reactive Flow (newest first).

```kotlin
@Query("SELECT * FROM packets ORDER BY timestamp DESC LIMIT :limit")
fun observePackets(limit: Int = 100): Flow<List<PacketEntity>>
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `limit` | `Int` | 100 | Maximum packets to return |
| **Returns** | `Flow<List<PacketEntity>>` | Reactive stream of packets |

**Behavior**:
- Emits new list whenever database changes
- Flow is lifecycle-aware (cancels with scope)
- Uses timestamp DESC index for ordering
- No replay - only emits current state on collection

**Usage**:
```kotlin
packetDao.observePackets(limit = 50)
    .collect { packets ->
        updateUI(packets)
    }
```

---

#### `observePacketsByAppId`

Observe packets for a specific appId as reactive Flow (newest first).

```kotlin
@Query("SELECT * FROM packets WHERE appId = :appId ORDER BY timestamp DESC LIMIT :limit")
fun observePacketsByAppId(appId: String, limit: Int = 100): Flow<List<PacketEntity>>
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `appId` | `String` | - | Application identifier to filter by |
| `limit` | `Int` | 100 | Maximum packets to return |
| **Returns** | `Flow<List<PacketEntity>>` | Reactive stream of packets for the appId |

**Behavior**:
- Filters packets to only those matching the given appId
- Uses composite index (appId, timestamp DESC) for efficient queries
- Emits new list whenever matching packets change

**Usage**:
```kotlin
packetDao.observePacketsByAppId("myapp", limit = 50)
    .collect { packets ->
        updateUI(packets)
    }
```

---

#### `observePacketCount`

Observe total packet count as reactive Flow.

```kotlin
@Query("SELECT COUNT(*) FROM packets")
fun observePacketCount(): Flow<Int>
```

| Returns | Type | Description |
|---------|------|-------------|
| **Returns** | `Flow<Int>` | Reactive count of all packets |

**Behavior**:
- Emits whenever packet count changes (insert/delete)
- Lightweight query (no data transfer)

**Usage**:
```kotlin
packetDao.observePacketCount()
    .collect { count ->
        statusText = "$count packets received"
    }
```

---

#### `observePacketCountByAppId`

Observe packet count for a specific appId.

```kotlin
@Query("SELECT COUNT(*) FROM packets WHERE appId = :appId")
fun observePacketCountByAppId(appId: String): Flow<Int>
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `appId` | `String` | Application identifier to filter by |
| **Returns** | `Flow<Int>` | Reactive count for the appId |

**Behavior**:
- Emits whenever count for the appId changes
- Uses appId index for efficient counting

**Usage**:
```kotlin
packetDao.observePacketCountByAppId("myapp")
    .collect { count ->
        statusText = "$count packets for myapp"
    }
```

---

#### `getPacketById`

Retrieve a single packet by ID (for detail view).

```kotlin
@Query("SELECT * FROM packets WHERE id = :id")
suspend fun getPacketById(id: Long): PacketEntity?
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `Long` | Packet ID |
| **Returns** | `PacketEntity?` | Packet or null if not found |

**Behavior**:
- One-shot query (not reactive)
- Returns null if ID doesn't exist
- Uses primary key index (O(1) lookup)

**Usage**:
```kotlin
val packet = packetDao.getPacketById(selectedId)
if (packet != null) {
    showDetailView(packet)
}
```

---

#### `deleteAllPackets`

Delete all packets from database (for "Erase All Data" feature).

```kotlin
@Query("DELETE FROM packets")
suspend fun deleteAllPackets(): Int
```

| Returns | Type | Description |
|---------|------|-------------|
| **Returns** | `Int` | Number of rows deleted |

**Behavior**:
- Executes single DELETE statement
- Returns count of deleted rows
- Triggers Flow updates (observers will emit empty list)

**Usage**:
```kotlin
val deleted = packetDao.deleteAllPackets()
Log.d(TAG, "Erased $deleted packets")
```

---

#### `deletePacketsByAppId`

Delete all packets for a specific appId.

```kotlin
@Query("DELETE FROM packets WHERE appId = :appId")
suspend fun deletePacketsByAppId(appId: String): Int
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `appId` | `String` | Application identifier to delete packets for |
| **Returns** | `Int` | Number of rows deleted |

**Behavior**:
- Deletes only packets matching the given appId
- Other apps' packets are preserved
- Triggers Flow updates for observers of this appId

**Usage**:
```kotlin
val deleted = packetDao.deletePacketsByAppId("myapp")
Log.d(TAG, "Erased $deleted packets for myapp")
```

---

## PacketDatabase

Room database singleton providing access to DAOs.

**Location**: `udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketDatabase.kt`

### Methods

#### `getInstance`

Get or create the database singleton.

```kotlin
companion object {
    fun getInstance(context: Context): PacketDatabase
}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `context` | `Context` | Android context (uses applicationContext internally) |
| **Returns** | `PacketDatabase` | Singleton database instance |

**Behavior**:
- Thread-safe (double-checked locking)
- Creates database on first call
- Returns existing instance on subsequent calls
- Always uses `applicationContext` internally

**Usage**:
```kotlin
// In Service
val database = PacketDatabase.getInstance(applicationContext)
val dao = database.packetDao()

// In Activity
val database = PacketDatabase.getInstance(this)
val dao = database.packetDao()
```

---

#### `packetDao`

Get the PacketDao instance.

```kotlin
abstract fun packetDao(): PacketDao
```

| Returns | Type | Description |
|---------|------|-------------|
| **Returns** | `PacketDao` | DAO for packet operations |

**Behavior**:
- Returns Room-generated implementation
- Same instance for lifetime of database

---

## PacketEntity

Room entity representing a stored packet.

**Location**: `udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketEntity.kt`

### Constructor

```kotlin
data class PacketEntity(
    val id: Long = 0,
    val appId: String,
    val data: ByteArray,
    val sourceIp: String,
    val sourcePort: Int,
    val timestamp: Long
)
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | `Long` | 0 | Auto-generated primary key |
| `appId` | `String` | - | Application identifier (indexed for filtering) |
| `data` | `ByteArray` | - | Packet payload without appId prefix (up to 64KB) |
| `sourceIp` | `String` | - | Source IP address |
| `sourcePort` | `Int` | - | Source UDP port |
| `timestamp` | `Long` | - | Reception time (epoch millis) |

### Extension Functions

Convert from UdpPacket to PacketEntity (parses appId prefix).

```kotlin
// Parse appId from packet and convert
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

// With pre-validated appId and payload
fun UdpPacket.toEntity(appId: String, payload: ByteArray): PacketEntity
```

---

## PacketParser

Utility for parsing the length-prefixed appId from packet data.

**Location**: `udp-service/src/main/kotlin/com/example/udpservice/api/PacketParser.kt`

### Wire Format

```
+--------+------------------+------------------+
| Length |      AppId       |     Payload      |
| 1 byte | Length bytes     | Remaining bytes  |
+--------+------------------+------------------+
```

### Methods

#### `parse`

```kotlin
fun parse(data: ByteArray): ParsedPacket?
```

Returns `ParsedPacket(appId, payload)` if valid, `null` otherwise.

#### `encode`

```kotlin
fun encode(appId: String, payload: ByteArray): ByteArray?
```

Returns encoded packet data, `null` if appId is empty or > 255 bytes.

---

## Error Handling

### Database Errors

| Error | Cause | Handling |
|-------|-------|----------|
| `SQLiteConstraintException` | Duplicate key (rare) | Log and continue |
| `SQLiteFullException` | Storage full | Log error, emit user notification |
| `IllegalStateException` | Database closed | Re-initialize singleton |

### Recommended Pattern

```kotlin
suspend fun persistPacket(packet: UdpPacket) {
    try {
        packetDao.insertPacket(packet.toEntity())
    } catch (e: Exception) {
        Log.e(TAG, "Failed to persist packet", e)
        // Continue receiving - don't let DB errors stop service
    }
}
```

---

## Thread Safety

| Operation | Thread | Safety |
|-----------|--------|--------|
| `insertPacket()` | IO (coroutine) | Room mutex protects writes |
| `insertPackets()` | IO (coroutine) | Transaction isolation |
| `observePackets()` | Main (collect) | Room provides read consistency |
| `deleteAllPackets()` | IO (coroutine) | Atomic DELETE |
| Concurrent read/write | Any | WAL mode prevents blocking |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-01 | Initial API definition |
