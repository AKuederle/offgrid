# Research: Message Persistence

**Feature**: 002-message-persistence
**Date**: 2026-02-01
**Purpose**: Resolve technical decisions and unknowns before implementation

## Executive Summary

This research validates the technical approach for persisting UDP packets to Room database. All NEEDS CLARIFICATION items have been resolved with concrete recommendations. Key findings:

1. **Room 2.6.1** is the recommended version (compatible with compileSdk 34, Kotlin 1.9.22)
2. **Persistence layer belongs in `udp-service` module** (service writes directly)
3. **Application context singleton pattern** for database (survives lifecycle changes)
4. **Instrumented tests required for DAO testing** (Room needs Android context)
5. **No Repository pattern needed** (direct DAO access follows YAGNI)

---

## Decision 1: Room Database Version

**Decision**: Use Room 2.6.1

**Rationale**:
- Latest stable version fully compatible with project stack
- Native Kotlin coroutine support (suspend functions, Flow)
- Verified compatibility: compileSdk 34, Kotlin 1.9.22, Coroutines 1.7.3

**Alternatives Considered**:
- Room 2.5.x: Older, missing some Flow optimizations
- Room 2.7.x: Not yet stable at time of writing

**Dependencies to Add**:
```kotlin
// udp-service/build.gradle.kts
plugins {
    id("kotlin-kapt")  // Required for Room annotation processing
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

---

## Decision 2: Database Initialization Pattern

**Decision**: Application context singleton with double-checked locking

**Rationale**:
- Application context survives service recreation after system kill
- Single database instance shared between service (writes) and UI (reads)
- Room handles concurrent access internally via WAL (Write-Ahead Logging)

**Pattern**:
```kotlin
@Database(entities = [PacketEntity::class], version = 1, exportSchema = false)
abstract class PacketDatabase : RoomDatabase() {
    abstract fun packetDao(): PacketDao

    companion object {
        @Volatile
        private var instance: PacketDatabase? = null

        fun getInstance(context: Context): PacketDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,  // Always applicationContext
                    PacketDatabase::class.java,
                    "packet_database.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
```

**Alternatives Considered**:
- Service-scoped database: Rejected - would recreate on service restart
- DI framework (Hilt): Rejected - adds complexity, project uses manual DI

---

## Decision 3: Entity Design for 64KB Packets

**Decision**: Single `PacketEntity` table with ByteArray BLOB storage

**Rationale**:
- Room handles ByteArray → SQLite BLOB automatically with no size limit
- No special handling needed for 64KB packets (SQLite supports up to 1GB)
- Index on timestamp DESC for efficient ORDER BY queries
- Separate sourceIp/sourcePort fields for future filtering

**Schema**:
```kotlin
@Entity(
    tableName = "packets",
    indices = [
        Index(value = ["timestamp"], orders = [Index.Order.DESC])
    ]
)
data class PacketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "data") val data: ByteArray,
    @ColumnInfo(name = "sourceIp") val sourceIp: String,
    @ColumnInfo(name = "sourcePort") val sourcePort: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
```

**Performance Notes**:
- Index on `timestamp DESC` provides O(log n) for ORDER BY
- DO NOT index `data` column (BLOB indexes are expensive)
- 10,000 packets at 64KB each = 640MB theoretical max (acceptable)

**Alternatives Considered**:
- Storing IP:port as combined string: Rejected - harder to query
- Storing payload as Base64: Rejected - wastes storage, slower
- Separate table for large payloads: Rejected - unnecessary complexity

---

## Decision 4: DAO Pattern

**Decision**: Interface with suspend functions for writes, Flow for reads

**Rationale**:
- Suspend functions enable non-blocking writes (critical for UDP reception)
- Flow provides automatic UI updates when database changes
- Room auto-wraps batch inserts in transactions

**Interface**:
```kotlin
@Dao
interface PacketDao {
    @Insert
    suspend fun insertPacket(packet: PacketEntity): Long

    @Insert
    suspend fun insertPackets(packets: List<PacketEntity>)

    @Query("SELECT * FROM packets ORDER BY timestamp DESC LIMIT :limit")
    fun observePackets(limit: Int = 100): Flow<List<PacketEntity>>

    @Query("SELECT COUNT(*) FROM packets")
    fun observePacketCount(): Flow<Int>

    @Query("SELECT * FROM packets WHERE id = :id")
    suspend fun getPacketById(id: Long): PacketEntity?

    @Query("DELETE FROM packets")
    suspend fun deleteAllPackets(): Int
}
```

**Alternatives Considered**:
- Repository wrapper: Rejected - YAGNI, DAO is already an interface
- LiveData instead of Flow: Rejected - project standardized on Coroutines

---

## Decision 5: Service Integration

**Decision**: Service writes to database directly in coroutine scope

**Rationale**:
- UdpReceiverService already has coroutine scope for socket operations
- Async writes don't block packet reception
- Graceful degradation on DB errors (log and continue)

**Integration Point**:
```kotlin
class UdpReceiverService : Service() {
    private val database by lazy { PacketDatabase.getInstance(applicationContext) }
    private val packetDao by lazy { database.packetDao() }

    // In packet collection coroutine:
    private suspend fun persistPacket(packet: UdpPacket) {
        try {
            val entity = PacketEntity(
                data = packet.data,
                sourceIp = packet.sourceAddress.hostString,
                sourcePort = packet.sourceAddress.port,
                timestamp = packet.timestamp
            )
            packetDao.insertPacket(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist packet", e)
            // Continue receiving - don't let DB errors stop service
        }
    }
}
```

**Alternatives Considered**:
- Separate persistence coroutine: Rejected - adds complexity, same scope works
- Batched writes: Could add later if needed for performance

---

## Decision 6: Testing Strategy

**Decision**: Instrumented tests in androidTest with in-memory database

**Rationale**:
- Room requires Android Context - cannot run on pure JVM
- In-memory database provides fast, isolated tests
- JUnit 4 runner required for Android instrumented tests
- Keep JUnit 5 for non-Room unit tests

**Test Structure**:
```
udp-service/
├── src/test/kotlin/                    # JUnit 5 unit tests (no Room)
│   └── com/example/udpservice/
│       └── api/UdpPacketTest.kt       # Existing
└── src/androidTest/kotlin/             # JUnit 4 instrumented tests
    └── com/example/udpservice/
        └── persistence/
            └── PacketDaoTest.kt       # Room DAO tests
```

**Test Template**:
```kotlin
@RunWith(AndroidJUnit4::class)
class PacketDaoTest {
    private lateinit var database: PacketDatabase
    private lateinit var dao: PacketDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PacketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.packetDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertPacket_retrievable() = runTest {
        val packet = PacketEntity(data = "test".toByteArray(), ...)
        val id = dao.insertPacket(packet)
        val retrieved = dao.getPacketById(id)
        assertEquals(packet.data.contentToString(), retrieved?.data?.contentToString())
    }
}
```

**Alternatives Considered**:
- Robolectric: Known issues with Room in-memory databases
- MockK for DAO: Defeats purpose of testing actual database logic

---

## Decision 7: Module Placement

**Decision**: Persistence layer in `udp-service` module

**Rationale**:
- Service writes packets (service lives in udp-service)
- Library-first architecture principle (self-contained module)
- Could be reused if another app consumes the library

**File Structure**:
```
udp-service/src/main/kotlin/com/example/udpservice/
├── persistence/
│   ├── PacketDatabase.kt
│   ├── PacketEntity.kt
│   └── PacketDao.kt
├── UdpReceiverService.kt  # Modified to use database
└── ...existing files...
```

**Alternatives Considered**:
- App module only: Rejected - service needs direct access
- Separate persistence module: Rejected - overkill for single table

---

## Decision 8: UI Integration

**Decision**: Replace in-memory PacketLog with database-backed Flow

**Rationale**:
- Current PacketLog is 100-packet in-memory limit
- Database observation via Flow provides same reactive pattern
- UI collects from DAO Flow, not in-memory list

**Changes Required**:
1. `MainActivity`: Collect from `packetDao.observePackets()` instead of `PacketLog.packets`
2. `PacketLog`: Remove or repurpose as cache layer (YAGNI - start without)
3. `BrokerScreen`: Add "Erase All Data" button (disabled when service running)

**Alternatives Considered**:
- Keep PacketLog as cache layer: Can add later if performance requires
- Paging library: Can add later if list scrolling performance degrades

---

## Open Questions (Resolved)

| Question | Resolution |
|----------|------------|
| Room version compatibility? | 2.6.1 verified with compileSdk 34, Kotlin 1.9.22 |
| Where does persistence layer live? | udp-service module |
| How to handle 64KB packets? | ByteArray BLOB - no size limit |
| Service context or Application context? | Application context always |
| Repository pattern needed? | No - direct DAO access (YAGNI) |
| JUnit 5 for Room tests? | No - use JUnit 4 @RunWith for instrumented tests |

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Database migration on schema change | Using `fallbackToDestructiveMigration()` for MVP; implement proper migrations before production |
| Performance with 10,000+ packets | Index on timestamp; can add pagination later |
| Memory pressure with large packets | Room only loads data when accessed; LazyColumn handles UI |
| Service dies during write | Async write; service continues; packet is logged but lost |

---

## Next Steps

1. **Phase 1**: Create data-model.md with final entity schema
2. **Phase 1**: Create contracts/ with internal API documentation
3. **Phase 1**: Create quickstart.md with developer setup guide
4. **Phase 2**: Generate tasks.md via `/speckit.tasks`
