# Quickstart: Buffered Send with Persistent Retry

**Feature**: 004-buffered-send
**Date**: 2026-02-02

## Overview

This feature adds persistent outbound message queuing with automatic retry and presence-based delivery resume. Messages are stored in the database before transmission, ensuring no message is lost even if the app is killed or the peer goes offline.

## Quick Setup

### 1. Build the Project

```bash
./gradlew assembleDebug
./gradlew :udp-cli:installDist
```

### 2. Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Run the Kotlin CLI

```bash
# Send a message
./udp-cli/build/install/udp-cli/bin/udp-cli send -h 192.168.1.46 -p 5000 -m "Hello"

# Send with app ID
./udp-cli/build/install/udp-cli/bin/udp-cli send -h 192.168.1.46 -p 5000 -a broker -m "Hello broker"

# Receive messages
./udp-cli/build/install/udp-cli/bin/udp-cli receive -p 5000

# Send presence broadcast
./udp-cli/build/install/udp-cli/bin/udp-cli broadcast -p 5000
```

## Test Scenarios

### Scenario 1: Basic Send with Persistence (US1)

**Goal**: Verify messages are stored before transmission

**Steps**:
1. Start the Android app and foreground service
2. From CLI: `udp-cli send -h <DEVICE_IP> -p 5000 -a broker -m "Test message"`
3. Message appears in app with "delivered" status

**Verification**:
- Message visible in app UI
- Database contains outbound_messages entry with status = DELIVERED

### Scenario 2: Retry on Failure (US2)

**Goal**: Verify exponential backoff retry works

**Steps**:
1. Send message to non-existent peer: `udp-cli send -h 192.168.1.254 -p 5000 -m "Test"`
2. Observe retry attempts in logs
3. After ~63 seconds, message moves to WAITING status

**Verification**:
- Retries at intervals: 1s, 2s, 4s, 8s, 16s, 32s
- After 6 retries, status = WAITING
- Total time ≈ 63 seconds

### Scenario 3: Resume on Peer Activity (US3)

**Goal**: Verify pending messages resume when peer comes back

**Setup**:
1. Peer A (Android app) has pending message for Peer B
2. Peer B was offline, message in WAITING status

**Steps**:
1. Peer B comes online and sends any message to Peer A
2. Peer A detects activity and resumes pending messages

**Verification**:
- Message status changes: WAITING → PENDING → SENDING → DELIVERED
- Resume happens within 1 second of peer activity

### Scenario 4: Presence Broadcast (US5)

**Goal**: Verify presence broadcast triggers delivery resume

**Setup**:
1. Device A has pending messages for Device B (in WAITING status)
2. Device B is offline

**Steps**:
1. Device B comes online and sends presence broadcast
2. Device A receives broadcast and resumes delivery

**Verification**:
- Presence broadcast received by Device A
- Pending messages resume delivery
- Messages delivered successfully

### Scenario 5: Kotlin CLI Replace Python (US6)

**Goal**: Verify Kotlin CLI works as Python replacement

**Steps**:
1. Build CLI: `./gradlew :udp-cli:installDist`
2. Send message: `udp-cli send -h <DEVICE_IP> -p 5000 -a broker -m "From Kotlin CLI"`
3. Message received by app

**Verification**:
- Same functionality as Python tool
- Uses reliable-udp library directly
- No Python dependencies required

## Integration Points

### With UdpSocket (udp-service)

```kotlin
// Existing UdpSocket now has send capability
val socket = UdpSocket()
socket.start(5000)

// Send message (returns immediately after persistence)
val result = socket.send(
    peer = InetSocketAddress("192.168.1.100", 5000),
    payload = "Hello".toByteArray()
)

when (result) {
    is SendResult.Queued -> println("Message queued: ${result.messageId}")
    is SendResult.Failed -> println("Failed: ${result.reason}")
}

// Observe delivery status
socket.outboundMessages.collect { messages ->
    messages.forEach { msg ->
        println("${msg.id}: ${msg.status}")
    }
}
```

### With ReliableSocket (reliable-udp)

```kotlin
// Presence message type added to protocol
enum class PacketType(val value: Byte) {
    DATA(0x01),
    ACK(0x02),
    PRESENCE(0x03)  // NEW
}

// Handle presence in receive loop
when (header.type) {
    PacketType.DATA -> handleDataPacket(...)
    PacketType.ACK -> handleAckPacket(...)
    PacketType.PRESENCE -> handlePresencePacket(source)
}
```

### With Room Database

```kotlin
// New DAO for outbound messages
@Dao
interface OutboundMessageDao {
    @Insert
    suspend fun insert(message: OutboundMessageEntity): Long

    @Query("SELECT * FROM outbound_messages WHERE status IN ('PENDING', 'RETRYING', 'WAITING')")
    fun observePending(): Flow<List<OutboundMessageEntity>>

    @Query("UPDATE outbound_messages SET status = :status WHERE peer_host = :host AND peer_port = :port AND status = 'WAITING'")
    suspend fun resumeForPeer(host: String, port: Int, status: String = "PENDING"): Int
}
```

## Performance Expectations

| Metric | Target | Notes |
|--------|--------|-------|
| Message persistence | <50ms | Room insert |
| Delivery (normal) | <3s | Including ACK |
| Retry total time | ~63s | 6 retries with backoff |
| Resume latency | <1s | After peer activity |
| Max pending/peer | 100+ | No significant degradation |

## Error Handling

| Error | Behavior |
|-------|----------|
| Network unavailable | Message stored, retry when connected |
| Peer unreachable | Exponential backoff, then WAITING |
| App killed | Messages persist, resume on restart |
| Storage full | SendResult.Failed with reason |
| Invalid peer address | SendResult.Failed immediately |

## Logging

Enable debug logging for troubleshooting:

```kotlin
// In Application or test setup
Log.d("SendQueue", "Message $id: $status → $newStatus")
Log.d("RetryScheduler", "Retry #$count in ${delay}ms")
Log.d("PresenceBroadcaster", "Broadcast sent to $address")
```

## Migration Notes

### From Python CLI

The Python `udp-sender` tool is replaced by `udp-cli`:

| Python | Kotlin |
|--------|--------|
| `uv run udp-sender send -h HOST -p PORT -m MSG` | `udp-cli send -h HOST -p PORT -m MSG` |
| `uv run udp-sender send -a broker ...` | `udp-cli send -a broker ...` |
| `uv run udp-sender flood ...` | Not implemented (use loop) |
| `uv run udp-sender interactive` | `udp-cli receive -p PORT` |

### Database Migration

Room migration adds `outbound_messages` table:
- Version 1 → 2
- Automatic migration via Room
- No data loss for existing `udp_packets` table
