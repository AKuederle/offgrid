# Data Model: App Prefix Notifications

## Entity Relationship Diagram

```
┌─────────────────────────────────────┐
│         AppRegistration             │
├─────────────────────────────────────┤
│ prefix: String (PK)                 │
│ packageName: String                 │
│ notificationsEnabled: Boolean       │
│ deepLinkUri: String                 │
│ createdAt: Long                     │
└─────────────────────────────────────┘
              │
              │ 1:N (prefix → appId)
              ▼
┌─────────────────────────────────────┐
│           PacketEntity              │
├─────────────────────────────────────┤
│ id: Long (PK, auto)                 │
│ appId: String (indexed)             │
│ data: ByteArray                     │
│ sourceIp: String                    │
│ sourcePort: Int                     │
│ timestamp: Long (indexed)           │
│ isRead: Boolean (default: false)    │  ← NEW
└─────────────────────────────────────┘
         │
         │ Indices: [appId], [timestamp DESC],
         │          [appId, timestamp], [appId, isRead]  ← NEW
         ▼
```

## Entity Definitions

### AppRegistrationEntity (NEW)

Stores prefix registration configuration. Designed for future cross-app support.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `prefix` | String | PRIMARY KEY | Unique app prefix identifier (e.g., "broker") |
| `packageName` | String | NOT NULL | Registering app's package name (for future AIDL) |
| `notificationsEnabled` | Boolean | NOT NULL | Whether to show notifications for this prefix |
| `deepLinkUri` | String | NOT NULL | URI to open when notification tapped |
| `createdAt` | Long | NOT NULL | Timestamp of registration (epoch millis) |

**Kotlin Definition**:
```kotlin
@Entity(tableName = "app_registrations")
data class AppRegistrationEntity(
    @PrimaryKey
    val prefix: String,
    val packageName: String,
    val notificationsEnabled: Boolean,
    val deepLinkUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### PacketEntity (MODIFIED)

Existing entity with added `isRead` field for unread tracking.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PRIMARY KEY, AUTO | Unique packet identifier |
| `appId` | String | INDEXED | App prefix this packet belongs to |
| `data` | ByteArray | NOT NULL | Raw packet payload (without appId prefix) |
| `sourceIp` | String | NOT NULL | Sender IP address |
| `sourcePort` | Int | NOT NULL | Sender port number |
| `timestamp` | Long | INDEXED | Receipt timestamp (epoch millis) |
| `isRead` | Boolean | NOT NULL, DEFAULT false | **NEW**: Whether user has viewed this packet |

**New Index**: `[appId, isRead]` for efficient unread count queries

**Kotlin Definition** (changes only):
```kotlin
@Entity(
    tableName = "packets",
    indices = [
        Index("appId"),
        Index("timestamp"),
        Index("appId", "timestamp"),
        Index("appId", "isRead")  // NEW
    ]
)
data class PacketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appId: String,
    val data: ByteArray,
    val sourceIp: String,
    val sourcePort: Int,
    val timestamp: Long,
    val isRead: Boolean = false  // NEW
)
```

## Database Schema

### Version 2 Migration

```sql
-- Migration from version 1 to 2

-- 1. Add isRead column to packets table
ALTER TABLE packets ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0;

-- 2. Create index for unread queries
CREATE INDEX IF NOT EXISTS index_packets_appId_isRead ON packets(appId, isRead);

-- 3. Create app_registrations table
CREATE TABLE IF NOT EXISTS app_registrations (
    prefix TEXT NOT NULL PRIMARY KEY,
    packageName TEXT NOT NULL,
    notificationsEnabled INTEGER NOT NULL,
    deepLinkUri TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
```

## Validation Rules

### AppRegistration

| Field | Validation |
|-------|------------|
| `prefix` | Non-empty, max 255 bytes UTF-8, alphanumeric + underscore |
| `packageName` | Valid Android package name format (for future use) |
| `deepLinkUri` | Valid URI format, must have scheme |

### PacketEntity

| Field | Validation |
|-------|------------|
| `appId` | Must match a registered prefix (enforced at receive time) |
| `isRead` | Boolean, defaults to false on insert |

## State Transitions

### Packet Read Status

```
┌──────────────┐     View message page     ┌────────────┐
│   isRead=    │ ─────────────────────────▶│  isRead=   │
│    false     │                           │    true    │
└──────────────┘                           └────────────┘
      │                                          │
      │ (no reverse transition - once read,      │
      │  always read)                            │
      ▼                                          ▼
```

### Registration Lifecycle

```
┌─────────────┐    registerPrefix()    ┌──────────────┐
│  Not        │ ─────────────────────▶ │  Registered  │
│  Registered │                        │              │
└─────────────┘                        └──────────────┘
                                             │
                                             │ unregisterPrefix()
                                             ▼
                                       ┌──────────────┐
                                       │  Removed     │
                                       │  (soft/hard) │
                                       └──────────────┘
```

## Query Patterns

### Unread Count per Prefix

```kotlin
@Query("SELECT COUNT(*) FROM packets WHERE appId = :appId AND isRead = 0")
fun observeUnreadCount(appId: String): Flow<Int>
```

### Mark Messages as Read

```kotlin
@Query("UPDATE packets SET isRead = 1 WHERE appId = :appId AND isRead = 0")
suspend fun markAllAsRead(appId: String): Int

@Query("UPDATE packets SET isRead = 1 WHERE id IN (:ids)")
suspend fun markAsRead(ids: List<Long>): Int
```

### Get Registration for Prefix

```kotlin
@Query("SELECT * FROM app_registrations WHERE prefix = :prefix")
suspend fun getRegistration(prefix: String): AppRegistrationEntity?

@Query("SELECT * FROM app_registrations WHERE notificationsEnabled = 1")
fun observeEnabledRegistrations(): Flow<List<AppRegistrationEntity>>
```

## Data Volume Considerations

- Expected: 2 prefixes for testing, designed for ~10 prefixes future
- Messages: 100+/minute peak, stored until user erases
- Unread count query is O(1) with index on `[appId, isRead]`
- Registration table: Very small, < 100 rows expected
