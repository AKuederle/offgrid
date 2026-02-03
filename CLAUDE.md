# android-udp-service Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-01

## Active Technologies
- Kotlin 1.9.22 + Jetpack Compose, Coroutines 1.7.3, Room 2.6.1 (002-message-persistence)
- Room database with appId-indexed packets for multi-app filtering (002-message-persistence)
- Reliable UDP: Selective Repeat ARQ with SACK, RFC 9002 RTT estimation, 64KB fragmentation (003-reliable-transport)
- Kotlin 1.9.x (001-udp-receiver-mvp)
- Kotlin 1.9.22, JVM 17 + Kotlin Coroutines 1.7.3, Room 2.6.1, reliable-udp library (004-buffered-send)
- Room database (extending existing schema from 002-message-persistence) (004-buffered-send)

## Project Structure

```text
app/                      # Android app module
udp-service/              # UDP service library module (depends on reliable-udp)
reliable-udp/             # Reliable UDP transport library (Kotlin JVM)
udp-cli/                  # Kotlin CLI tool for testing
specs/                    # Feature specifications
```

## Prerequisites

### Gradle Setup

The project uses the Gradle wrapper (`./gradlew`). If you need standalone Gradle:

**Location (via Android Studio):**
- Wrapper distributions: `~/.gradle/wrapper/dists/`
- Example: `~/.gradle/wrapper/dists/gradle-8.5-bin/*/gradle-8.5/bin/gradle`

**Adding to PATH (optional):**
```bash
# Add to ~/.bashrc or ~/.zshrc
export PATH="$HOME/.gradle/wrapper/dists/gradle-8.5-bin/$(ls ~/.gradle/wrapper/dists/gradle-8.5-bin)/gradle-8.5/bin:$PATH"
```

**Or use the wrapper (recommended):**
```bash
./gradlew <task>
```

### Android Studio

Location: `/opt/android-studio`

Launch: `/opt/android-studio/bin/studio.sh`

## Commands

```bash
# Build project
./gradlew assembleDebug

# Run unit tests
./gradlew :reliable-udp:test      # Reliable UDP library tests
./gradlew :udp-service:test       # UDP service tests
./gradlew :udp-cli:test           # CLI tool tests
./gradlew :app:test               # App tests

# Run on-device tests (requires connected device)
./gradlew :app:connectedAndroidTest

# Kotlin CLI tool
./gradlew :udp-cli:installDist    # Build CLI distribution
./udp-cli/build/install/udp-cli/bin/udp-cli send -h HOST -p 5000 -a "broker" -m "message"
./udp-cli/build/install/udp-cli/bin/udp-cli receive -p 5000 -a "broker"
./udp-cli/build/install/udp-cli/bin/udp-cli broadcast -p 5000
```

## Code Style

Kotlin 1.9.x: Follow standard conventions

## Recent Changes
- 004-buffered-send: Added Kotlin 1.9.22, JVM 17 + Kotlin Coroutines 1.7.3, Room 2.6.1, reliable-udp library
- 003-reliable-transport: Added reliable-udp module with Selective Repeat ARQ, SACK, RFC 9002 RTT estimation, 64KB message fragmentation, delivery callbacks. Python tool and UdpSocket now always use reliable transport.
- 002-message-persistence: Added Room 2.6.1 persistence with appId prefix filtering, packet detail view, erase functionality

<!-- MANUAL ADDITIONS START -->
## Testing with the Android App

**Always use the app-id prefix** when sending messages to the Android app:
```bash
./gradlew :udp-cli:installDist
./udp-cli/build/install/udp-cli/bin/udp-cli send -h <DEVICE_IP> -p 5000 -a "broker" -m "your message"
```

Messages without the `-a "broker"` prefix will be received but filtered out by the app's UI (it only displays messages matching its configured appId).
<!-- MANUAL ADDITIONS END -->
