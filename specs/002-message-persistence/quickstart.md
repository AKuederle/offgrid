# Quickstart: Message Persistence

**Feature**: 002-message-persistence
**Date**: 2026-02-01

## Overview

This guide explains how to work with the message persistence feature during development.

## Prerequisites

- Android Studio (Hedgehog or later)
- Android device or emulator (API 29+)
- Project cloned and building successfully

## Building

```bash
# Build the entire project
./gradlew assembleDebug

# Build only the udp-service library
./gradlew :udp-service:assembleDebug
```

## Running Tests

### Unit Tests (JVM)

```bash
# Run all unit tests
./gradlew :udp-service:test

# Run specific test class
./gradlew :udp-service:test --tests "com.example.udpservice.api.UdpPacketTest"
```

### Instrumented Tests (Emulator/Device)

```bash
# Run all instrumented tests (requires connected device)
./gradlew :udp-service:connectedAndroidTest

# Run only emulator-compatible tests (excludes @DeviceOnly)
./gradlew :udp-service:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.udpservice.test.DeviceOnly

# Run only persistence tests
./gradlew :udp-service:connectedAndroidTest \
  --tests "com.example.udpservice.persistence.*"
```

### End-to-End Tests (App Module)

```bash
# Run app integration tests
./gradlew :app:connectedAndroidTest
```

## Manual Testing

### Test Scenario 1: Basic Persistence

1. Install and launch the app
2. Start the UDP service (tap Start button)
3. Send test packets using the Python tool:
   ```bash
   cd tools/udp-sender
   uv run udp-sender send -h <device-ip> -m "Test message"
   ```
4. Verify packets appear in the message log
5. Background the app (press Home)
6. Send more packets
7. Return to the app - all packets should be visible

### Test Scenario 2: App Kill Recovery

1. Start the service and send packets
2. Swipe the app from recents (kills app process)
3. Verify notification remains visible (service still running)
4. Send more packets
5. Reopen the app
6. All packets (before and after kill) should be visible

### Test Scenario 3: Large Packet (64KB)

```bash
cd tools/udp-sender
# Send 64KB packet
uv run udp-sender send -h <device-ip> -m "$(python3 -c 'print("X" * 65000)')"
```

Verify:
- Packet is stored without truncation
- Can view full payload in detail view
- No crash or lag

### Test Scenario 4: Erase All Data

1. Stop the service first
2. Tap "Erase All Data" button
3. Confirm data is cleared (0 packets)
4. Restart service - should work normally

## Debugging

### View Database Contents

Using Android Studio:
1. View > Tool Windows > App Inspection
2. Select your running app
3. Database Inspector tab
4. Browse `packet_database.db` > `packets` table

Using ADB:
```bash
# Pull database file
adb shell "run-as com.example.udpbroker cat /data/data/com.example.udpbroker/databases/packet_database.db" > packet_database.db

# Open with sqlite3
sqlite3 packet_database.db "SELECT id, sourceIp, sourcePort, timestamp FROM packets ORDER BY timestamp DESC LIMIT 10"
```

### Check Logs

```bash
# Service logs
adb logcat -s UdpReceiverService

# Database operations
adb logcat -s PacketDatabase

# All app logs
adb logcat | grep com.example
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Packets not persisting | Database not initialized | Check service onCreate() |
| Empty list after restart | Using wrong context | Ensure applicationContext |
| UI not updating | Flow not collected | Check lifecycle scope |
| "Erase" button not working | Service still running | Stop service first |

## Dependencies Added

The following dependencies were added for this feature:

```kotlin
// udp-service/build.gradle.kts
plugins {
    id("kotlin-kapt")
}

dependencies {
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Testing
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

## File Locations

### Source Files

```
udp-service/src/main/kotlin/com/example/udpservice/
├── persistence/
│   ├── PacketDatabase.kt      # Database singleton
│   ├── PacketEntity.kt        # Room entity
│   └── PacketDao.kt           # Data access object
└── UdpReceiverService.kt      # Modified to persist packets
```

### Test Files

```
udp-service/src/androidTest/kotlin/com/example/udpservice/
└── persistence/
    └── PacketDaoTest.kt       # DAO instrumented tests

app/src/androidTest/kotlin/com/example/udpbroker/
└── PersistenceE2ETest.kt      # End-to-end tests
```

## API Usage Examples

### Persisting a Packet (Service)

```kotlin
// In UdpReceiverService
private val database by lazy { PacketDatabase.getInstance(applicationContext) }
private val packetDao by lazy { database.packetDao() }

private suspend fun persistPacket(packet: UdpPacket) {
    val entity = PacketEntity(
        data = packet.data,
        sourceIp = packet.sourceAddress.hostString,
        sourcePort = packet.sourceAddress.port,
        timestamp = packet.timestamp
    )
    packetDao.insertPacket(entity)
}
```

### Observing Packets (UI)

```kotlin
// In MainActivity or ViewModel
val database = PacketDatabase.getInstance(applicationContext)
val packetDao = database.packetDao()

// Collect in lifecycle scope
lifecycleScope.launch {
    packetDao.observePackets(limit = 100)
        .collect { packets ->
            // Update UI
        }
}
```

### Erasing Data

```kotlin
// Only when service is stopped
if (receiverState is ReceiverState.Stopped) {
    lifecycleScope.launch {
        val deleted = packetDao.deleteAllPackets()
        showToast("Erased $deleted packets")
    }
}
```
