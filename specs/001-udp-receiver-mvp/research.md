# Research: UDP Receiver MVP

**Date**: 2026-02-01
**Feature**: 001-udp-receiver-mvp

## Research Questions

### 1. Android Foreground Service for UDP (API 29+)

**Question**: What are the requirements for running a long-lived UDP listener as a foreground service on Android 10+?

**Decision**: Use `foregroundServiceType="specialUse"` with proper justification.

**Rationale**:
- Android 10 (API 29) requires foreground services for long-running background work
- Android 14 (API 34) requires explicit `foregroundServiceType` declaration
- "specialUse" is appropriate for local network communication that doesn't fit other categories
- Must provide `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explaining the use case

**Key Implementation Details**:
```xml
<service
    android:name=".UdpReceiverService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Local network UDP communication" />
</service>
```

**Permissions Required**:
- `INTERNET` - for UDP socket
- `FOREGROUND_SERVICE` - for running in foreground
- `FOREGROUND_SERVICE_SPECIAL_USE` - for specialUse type
- `POST_NOTIFICATIONS` - runtime permission on Android 13+

**Alternatives Considered**:
- WorkManager: Rejected - designed for deferrable tasks, not real-time packet reception
- Background service without foreground: Rejected - killed by system on Android 10+
- `dataSync` foreground type: Rejected - semantically incorrect, may face Play Store scrutiny

---

### 2. Coroutine-Based UDP Socket Pattern

**Question**: How should we wrap `java.net.DatagramSocket` for use with Kotlin coroutines?

**Decision**: Use `Dispatchers.IO` with `SharedFlow` for packet emission.

**Rationale**:
- `DatagramSocket.receive()` is blocking - must run on IO dispatcher
- `SharedFlow` allows multiple collectors (UI, logging, etc.) without backpressure issues
- `SupervisorJob` prevents socket errors from canceling the entire scope

**Pattern**:
```kotlin
class UdpSocket(private val port: Int) {
    private val _packets = MutableSharedFlow<UdpPacket>(extraBufferCapacity = 64)
    val packets: SharedFlow<UdpPacket> = _packets.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            val socket = DatagramSocket(port)
            val buffer = ByteArray(65535)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet) // Blocking call on IO thread
                _packets.emit(UdpPacket(...))
            }
        }
    }
}
```

**Alternatives Considered**:
- NIO Selector: Rejected - overkill for single-port reception, adds complexity
- `Channel` instead of `Flow`: Rejected - Flow integrates better with Compose
- `callbackFlow`: Rejected - SharedFlow is simpler for broadcast scenarios

---

### 3. Testing UDP Without Real Network

**Question**: How can we unit test UDP socket logic without requiring actual network operations?

**Decision**: Extract interface for socket operations, use TestDispatcher for coroutine tests.

**Rationale**:
- Interface allows mocking in unit tests
- `TestDispatcher` enables deterministic coroutine testing
- Actual UDP tests belong in on-device tests where real network is available

**Pattern**:
```kotlin
// Interface for testability
interface UdpReceiver {
    val packets: Flow<UdpPacket>
    fun start()
    fun stop()
}

// Unit test with mock
class UdpReceiverServiceTest {
    @Test
    fun `emits packets to flow`() = runTest {
        val mockReceiver = mockk<UdpReceiver>()
        every { mockReceiver.packets } returns flowOf(testPacket)
        // ...
    }
}
```

**Alternatives Considered**:
- Robolectric: Rejected - constitution explicitly discourages it
- LocalServerSocket for testing: Rejected - TCP, not UDP; adds complexity
- Emulator with adb port forwarding: Rejected - on-device test, not unit test

---

### 4. Displaying Network Interface IP

**Question**: How to reliably get the device's WiFi IP address to display to the user?

**Decision**: Use `NetworkInterface.getNetworkInterfaces()` filtered for WiFi.

**Rationale**:
- Works without additional permissions
- Returns all interfaces; filter for non-loopback IPv4
- More reliable than `WifiManager.getConnectionInfo()` which is deprecated

**Pattern**:
```kotlin
fun getLocalIpAddresses(): List<String> {
    return NetworkInterface.getNetworkInterfaces()?.toList()
        ?.flatMap { it.inetAddresses.toList() }
        ?.filter { !it.isLoopbackAddress && it is Inet4Address }
        ?.mapNotNull { it.hostAddress }
        ?: emptyList()
}
```

**Alternatives Considered**:
- `WifiManager.getConnectionInfo()`: Rejected - deprecated, requires ACCESS_WIFI_STATE
- ConnectivityManager with NetworkCallback: Rejected - overkill for display purposes
- Hardcoded assumption of wlan0: Rejected - not portable across devices

---

### 5. Python CLI Framework

**Question**: Which CLI framework for the Python UDP sender tool?

**Decision**: Use `click` with `uv run` for zero-install execution.

**Rationale**:
- click is lightweight, well-documented, and handles subcommands naturally
- uv manages dependencies automatically via pyproject.toml
- No need for heavy frameworks like Typer for this simple tool

**pyproject.toml structure**:
```toml
[project]
name = "udp-sender"
version = "0.1.0"
dependencies = ["click>=8.0"]

[project.scripts]
udp-sender = "udp_sender.cli:main"
```

**Alternatives Considered**:
- argparse (stdlib): Rejected - more verbose, subcommand handling is clunky
- Typer: Rejected - overkill, adds pydantic dependency
- Fire: Rejected - magic is confusing, poor error messages

---

### 6. Service-to-UI Communication

**Question**: How should the foreground service communicate received packets to the UI?

**Decision**: Bind service and expose packets as `StateFlow`.

**Rationale**:
- Bound service pattern is standard Android approach
- `StateFlow` integrates with Compose's `collectAsState()`
- Avoids complexity of broadcast receivers or event buses

**Pattern**:
```kotlin
// In Service
class UdpReceiverService : Service() {
    private val _packets = MutableStateFlow<List<UdpPacket>>(emptyList())
    val packets: StateFlow<List<UdpPacket>> = _packets.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService() = this@UdpReceiverService
    }
}

// In Composable
val packets by service.packets.collectAsState()
```

**Alternatives Considered**:
- LiveData: Rejected - StateFlow is more Kotlin-idiomatic
- LocalBroadcastManager: Rejected - deprecated
- EventBus: Rejected - adds dependency, not needed for simple case

---

## Summary of Decisions

| Topic | Decision | Key Reason |
|-------|----------|------------|
| Foreground Service Type | `specialUse` | Only valid option for local network UDP |
| UDP Socket Pattern | SharedFlow + IO Dispatcher | Non-blocking, multiple collectors |
| Testing Strategy | Interface extraction + mocks | Unit testable without network |
| IP Address Display | NetworkInterface enumeration | No extra permissions, reliable |
| Python CLI | click + uv | Simple, zero-install |
| Service-UI Communication | Bound service + StateFlow | Standard Android + Compose-friendly |
