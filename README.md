# Android UDP Service

A local-first Android UDP communication library that receives UDP packets without cloud dependencies.

## Overview

This project provides a foreground service that listens for UDP packets on your local network. No Google Play Services, no push notifications, no cloud infrastructure required.

**Current Status:** MVP complete (receive-only mode)

## Features

- Receive UDP packets on configurable port (default: 5000)
- Display packets in real-time with sender info and timestamps
- Foreground service with silent notification (won't disturb you)
- Auto-start on device boot (optional)
- Works on local network without internet

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator (API 29+ / Android 10+)
- Python 3.10+ with `uv` (for the test sender tool)

## Quick Start

### 1. Build and Install

```bash
# Clone the repository
git clone https://github.com/anthropics/android-udp-service.git
cd android-udp-service

# Build the APK
./gradlew :app:assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Start the Service

1. Open "UDP Broker" app on your device
2. Tap "Start" button
3. Note the IP address shown (e.g., `192.168.1.100:5000`)

### 3. Send Test Packets

From your computer (must be on the same network):

```bash
cd tools/udp-sender

# Install dependencies
uv sync

# Send a single packet
uv run udp-sender send -h 192.168.1.100 -m "Hello from PC!"

# Send multiple packets
uv run udp-sender flood -h 192.168.1.100 -r 10 -d 5
```

You should see packets appear in the app immediately.

## Python Sender Tool

The `udp-sender` tool in `tools/udp-sender/` provides commands for testing:

### Send Command
Send a single UDP packet:
```bash
uv run udp-sender send -h <device-ip> -m "Your message"
uv run udp-sender send -h <device-ip> -p 5000 -m "Custom port"
```

### Flood Command
Send packets at a specified rate:
```bash
# 100 packets/sec for 10 seconds (default)
uv run udp-sender flood -h <device-ip>

# Custom rate and duration
uv run udp-sender flood -h <device-ip> -r 50 -d 30  # 50/sec for 30 seconds
```

### Interactive Mode
Send packets interactively:
```bash
uv run udp-sender interactive -h <device-ip>
# Type messages, press Enter to send, Ctrl+C to quit
```

## Project Structure

```
android-udp-service/
├── app/                      # Android app module
│   └── src/main/kotlin/
│       ├── MainActivity.kt   # UI with service binding
│       ├── PacketLog.kt      # Packet history state
│       └── ui/BrokerScreen.kt
│
├── udp-service/              # Library module (AAR)
│   └── src/main/kotlin/
│       ├── UdpReceiver.kt    # Public interface
│       ├── UdpSocket.kt      # Socket implementation
│       ├── UdpReceiverService.kt
│       ├── NetworkUtils.kt   # IP detection
│       └── api/              # Data classes
│
└── tools/udp-sender/         # Python test tool
    └── src/udp_sender/
        ├── cli.py            # Click commands
        └── sender.py         # UdpSender class
```

## Development

### Test-Driven Development

This project follows strict TDD. All code must have tests written first.

### Running Tests

```bash
# Unit tests (fast, JVM-only)
./gradlew test

# Emulator tests (requires connected emulator)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.example.udpservice.test.DeviceOnly

# Device-only tests (requires physical device)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.udpservice.test.DeviceOnly

# Python tool tests
cd tools/udp-sender && uv run pytest
```

### Test Stages

| Stage | Location | Runs On | Purpose |
|-------|----------|---------|---------|
| Unit | `test/` | JVM | Business logic, no Android deps |
| Emulator | `androidTest/` | Emulator | Service lifecycle, localhost UDP |
| Device | `androidTest/` (@DeviceOnly) | Physical | Real network, WiFi IP |

### ADB Commands

Control the service via ADB broadcasts:

```bash
# Start service on port 5000
adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver \
  -a com.example.udpbroker.START_SERVICE --ei port 5000

# Stop service
adb shell am broadcast -n com.example.udpbroker/.ServiceControlReceiver \
  -a com.example.udpbroker.STOP_SERVICE
```

## Technical Details

- **Minimum SDK:** API 29 (Android 10)
- **Target SDK:** API 34
- **Language:** Kotlin 1.9.x with Coroutines
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM with StateFlow

## Roadmap

- [x] MVP: Receive and display UDP packets
- [ ] Send UDP packets from Android
- [ ] Message buffering with Room database
- [ ] Broker mode for multi-app sharing
- [ ] Topic-based message routing

## License

MIT
