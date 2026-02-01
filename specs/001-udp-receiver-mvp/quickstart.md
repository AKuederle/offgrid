# Quickstart: UDP Receiver MVP

**Date**: 2026-02-01
**Feature**: 001-udp-receiver-mvp

## Prerequisites

Before starting development, ensure you have:

| Tool | Version | Purpose |
|------|---------|---------|
| Android Studio | Hedgehog or later | Android development IDE |
| JDK | 17+ | Kotlin/Gradle compilation |
| Android SDK | API 29+ | Target platform |
| Physical Android device | Android 10+ | Testing (emulator not recommended for UDP) |
| uv | Latest | Python package manager |
| adb | From SDK | Device deployment |

## Project Setup

### 1. Clone and Open

```bash
git clone <repo-url>
cd android-udp-service
git checkout 001-udp-receiver-mvp
```

Open in Android Studio. Wait for Gradle sync.

### 2. Verify Build

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

### 3. Connect Device

```bash
# Enable USB debugging on device, then:
adb devices
# Should show your device
```

### 4. Install App

```bash
./gradlew :app:installDebug
```

Or run from Android Studio (green play button).

## Testing Workflow

### 1. Start the Service

1. Open "UDP Receiver" app on device
2. Tap "Start Service"
3. Grant notification permission if prompted
4. Note the IP address and port shown (e.g., `192.168.1.100:5000`)

### 2. Send Test Packet (Python Tool)

```bash
cd tools/udp-sender
uv run udp-sender send -h 192.168.1.100 -m "Hello from PC!"
```

### 3. Verify Reception

- Check the app's packet log
- Should show: `Hello from PC!` with timestamp and source IP

### 4. Flood Test

```bash
uv run udp-sender flood -h 192.168.1.100 -r 100 -d 5
# Sends 500 packets over 5 seconds
```

Verify all packets appear in the log.

## Development Workflow (TDD)

### Running Tests

```bash
# Unit tests (JVM)
./gradlew :udp-service:test
./gradlew :app:test

# On-device tests (requires connected device)
./gradlew :udp-service:connectedAndroidTest
./gradlew :app:connectedAndroidTest

# Python tool tests
cd tools/udp-sender
uv run pytest
```

### TDD Cycle

1. **Write failing test** in `src/test/` (unit) or `src/androidTest/` (device)
2. **Run test** - verify it fails (Red)
3. **Write minimal implementation** to pass test
4. **Run test** - verify it passes (Green)
5. **Refactor** while keeping tests green

### Example: Adding a Feature

```kotlin
// 1. Write test first (src/test/kotlin/.../UdpPacketTest.kt)
@Test
fun `displayText returns hex for binary data`() {
    val packet = UdpPacket(
        data = byteArrayOf(0x00, 0x01, 0xFF.toByte()),
        sourceAddress = InetSocketAddress("127.0.0.1", 5000)
    )
    assertEquals("00 01 FF", packet.displayText)
}

// 2. Run test - FAILS (class doesn't exist yet)
// 3. Implement UdpPacket.displayText
// 4. Run test - PASSES
// 5. Refactor if needed
```

## Project Structure Quick Reference

```
android-udp-service/
├── app/                    # UI application
│   └── src/main/kotlin/    # MainActivity, Compose screens
├── udp-service/            # Library module
│   └── src/main/kotlin/    # UdpSocket, Service, Packet
├── tools/udp-sender/       # Python test tool
└── specs/001-*/            # This feature's documentation
```

## Common Issues

### "Permission denied" on start
- Grant notification permission in device settings

### "Address already in use"
- Another app is using port 5000
- Kill other apps or change port (future feature)

### Packets not appearing
- Verify device and PC are on same WiFi network
- Check firewall isn't blocking UDP on PC
- Verify IP address in app matches device's actual IP

### Python tool "command not found"
- Run from `tools/udp-sender` directory
- Use `uv run udp-sender` not just `udp-sender`

## Next Steps

After completing MVP:
1. Run `/speckit.tasks` to generate implementation tasks
2. Follow TDD workflow for each task
3. Create PR when all tests pass
