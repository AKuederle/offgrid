# Android UDP Service

A local-first Android UDP communication library that receives UDP packets without cloud dependencies.

## Overview

This project provides a foreground service that listens for UDP packets on your local network. No Google Play Services, no push notifications, no cloud infrastructure required.

**Current Status:** Send and receive with reliable transport, message persistence, and buffered retry

## Features

- Receive and send UDP packets on configurable port (default: 5000)
- Reliable transport with automatic retransmission and delivery confirmation
- Message persistence with Room database (survives app restarts)
- Buffered send queue with exponential backoff retry
- Display packets in real-time with sender info and timestamps
- Outbound message status visibility (pending, sending, delivered, waiting)
- Foreground service with silent notification (won't disturb you)
- Auto-start on device boot (optional)
- Works on local network without internet

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator (API 29+ / Android 10+)

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
# Build the CLI tool
./gradlew :udp-cli:installDist

# Send a single packet (use -a "broker" for app-id prefix)
./udp-cli/build/install/udp-cli/bin/udp-cli send -h 192.168.1.100 -p 5000 -a "broker" -m "Hello from PC!"

# Receive packets
./udp-cli/build/install/udp-cli/bin/udp-cli receive -p 5000 -a "broker"

# Send presence broadcast
./udp-cli/build/install/udp-cli/bin/udp-cli broadcast -p 5000
```

You should see packets appear in the app immediately.

## Project Structure

```
android-udp-service/
├── app/                      # Android app module
│   └── src/main/kotlin/
│       ├── MainActivity.kt   # UI with service binding
│       └── ui/               # Compose UI components
│
├── udp-service/              # Library module (AAR)
│   └── src/main/kotlin/
│       ├── UdpReceiver.kt    # Public interface
│       ├── UdpSocket.kt      # Socket implementation
│       ├── UdpReceiverService.kt
│       ├── send/             # Outbound message queue
│       ├── presence/         # Peer discovery broadcast
│       └── persistence/      # Room database
│
├── reliable-udp/             # Reliable transport library (JVM)
│   └── src/main/kotlin/
│       ├── ReliableSocket.kt # Public interface
│       └── protocol/         # Wire protocol
│
└── udp-cli/                  # Kotlin CLI tool
    └── src/main/kotlin/
        └── commands/         # send, receive, broadcast
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
- [x] Reliable transport with retransmission
- [x] Message persistence with Room database
- [x] Send UDP packets with buffered retry
- [x] Delivery status visibility in UI
- [x] Presence broadcast for peer discovery
- [x] Kotlin CLI tool (replaced Python tool)
- [ ] Broker mode for multi-app sharing
- [ ] Topic-based message routing

## License

MIT
