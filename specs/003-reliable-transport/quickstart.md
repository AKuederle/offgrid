# Quickstart: Reliable Transport Layer

**Feature**: 003-reliable-transport
**Date**: 2026-02-01

## Prerequisites

- Android Studio with Kotlin 1.9.x
- Project cloned with existing `udp-service` module
- JDK 17+

## Module Setup

### 1. Create the Module

Add to `settings.gradle.kts`:

```kotlin
include(":reliable-udp")
```

Create `reliable-udp/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
```

### 2. Add Dependency to udp-service

Update `udp-service/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":reliable-udp"))
    // ... existing dependencies
}
```

## Basic Usage

### Receiving Messages

```kotlin
import com.example.reliableudp.ReliableSocket
import com.example.reliableudp.ReliableSocketImpl
import kotlinx.coroutines.flow.collect

val socket = ReliableSocketImpl()
socket.bind(5000)

// Collect complete messages (fragments automatically reassembled)
socket.messages.collect { message ->
    val text = message.payload.decodeToString()
    println("From ${message.source}: $text")
}
```

### Sending Messages

```kotlin
import com.example.reliableudp.ReliableSocket
import com.example.reliableudp.DeliveryResult
import java.net.InetSocketAddress

val socket = ReliableSocketImpl()
socket.bind(0) // Bind to any available port

val destination = InetSocketAddress("192.168.1.100", 5000)
val payload = "Hello, reliable UDP!".toByteArray()

// Synchronous send with delivery confirmation
when (val result = socket.send(destination, payload)) {
    is DeliveryResult.Success -> {
        println("Delivered! messageId=${result.messageId}")
    }
    is DeliveryResult.Failure -> {
        println("Failed: ${result.reason} after ${result.retryCount} retries")
    }
}
```

### Async Send with Callback

```kotlin
socket.sendAsync(destination, payload) { result ->
    when (result) {
        is DeliveryResult.Success -> println("Delivered!")
        is DeliveryResult.Failure -> println("Failed: ${result.reason}")
    }
}
```

### Large Messages (Automatic Fragmentation)

```kotlin
// Messages up to 64KB are automatically fragmented
val largePayload = ByteArray(64 * 1024) { it.toByte() }

// Send works the same - fragmentation is transparent
val result = socket.send(destination, largePayload)
```

## Integration with UdpReceiver

The reliable layer integrates seamlessly with the existing `UdpReceiver` interface:

```kotlin
// In UdpSocket.kt - the implementation change is internal
class UdpSocket(
    private val reliableSocket: ReliableSocket = ReliableSocketImpl(),
    // ... existing parameters
) : UdpReceiver {

    override val packets: SharedFlow<UdpPacket>
        // Reliable layer emits complete messages, not raw fragments

    override suspend fun start(port: Int) {
        reliableSocket.bind(port)
        // ... rest of implementation
    }
}
```

## Testing

### Unit Tests (Stage 1)

```bash
./gradlew :reliable-udp:test
```

### Integration Tests (Stage 2)

```bash
./gradlew :udp-service:connectedAndroidTest
```

## Wire Protocol Reference

### Packet Header (11 bytes)

```
┌──────────┬──────────┬───────────┬───────────┬──────────────┐
│ Type (1) │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
└──────────┴──────────┴───────────┴───────────┴──────────────┘
```

### Packet Types

| Type | Value | Description |
|------|-------|-------------|
| DATA | 0x01 | Payload packet, requires ACK |
| ACK | 0x02 | Acknowledgment with SACK ranges |
| PING | 0x03 | Keep-alive / RTT measurement |

### ACK Frame Format

```
┌─────────────────┬────────────┬────────────┬─────────────────┐
│ LargestAcked(4) │ Delay(2)   │ Count(1)   │ Ranges...       │
└─────────────────┴────────────┴────────────┴─────────────────┘

Each Range: GapSize(2) + AckCount(2) = 4 bytes
```

## Constants

| Constant | Value | Description |
|----------|-------|-------------|
| MAX_PAYLOAD_SIZE | 1400 bytes | Fragment size threshold |
| MAX_MESSAGE_SIZE | 64 KB | Maximum message size |
| MAX_RETRIES | 10 | Retransmit attempts |
| INITIAL_RTT | 333 ms | RTT before samples |
| FRAGMENT_TIMEOUT | 30 s | Incomplete message discard |

## Troubleshooting

### Messages Not Delivered

1. Check if receiver is bound: `socket.state.collect { println(it) }`
2. Verify network connectivity with PING packets
3. Check RTT estimate: high RTT means longer timeouts

### High Memory Usage

1. Reduce `ACK_QUEUE_CAPACITY` if needed
2. Check for fragment buffer leaks (incomplete messages timing out)
3. Ensure `close()` is called on socket disposal

### Duplicate Messages

The deduplication cache should prevent this. If occurring:
1. Check `DEDUP_CACHE_SIZE` is sufficient
2. Verify sequence numbers are strictly increasing
