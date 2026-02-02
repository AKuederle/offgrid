# android-udp-service Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-01

## Active Technologies
- Kotlin 1.9.22 + Jetpack Compose, Coroutines 1.7.3, Room 2.6.1 (002-message-persistence)
- Room database with appId-indexed packets for multi-app filtering (002-message-persistence)
- Reliable UDP: Selective Repeat ARQ with SACK, RFC 9002 RTT estimation, 64KB fragmentation (003-reliable-transport)
- Kotlin 1.9.x, Python 3.10+ (001-udp-receiver-mvp)

## Project Structure

```text
app/                      # Android app module
udp-service/              # UDP service library module (depends on reliable-udp)
reliable-udp/             # Reliable UDP transport library (Kotlin JVM)
tools/udp-sender/         # Python test tool (always uses reliable UDP)
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
./gradlew :app:test               # App tests

# Run on-device tests (requires connected device)
./gradlew :app:connectedAndroidTest

# Python tool
cd tools/udp-sender && uv run udp-sender --help
cd tools/udp-sender && uv run udp-sender send -h HOST -p 5000 -a "broker" -m "message"
cd tools/udp-sender && uv run pytest
cd tools/udp-sender && uv run ruff check .
```

## Code Style

Kotlin 1.9.x, Python 3.10+: Follow standard conventions

## Recent Changes
- 003-reliable-transport: Added reliable-udp module with Selective Repeat ARQ, SACK, RFC 9002 RTT estimation, 64KB message fragmentation, delivery callbacks. Python tool and UdpSocket now always use reliable transport.
- 002-message-persistence: Added Room 2.6.1 persistence with appId prefix filtering, packet detail view, erase functionality
- 001-udp-receiver-mvp: Added Kotlin 1.9.x, Python 3.10+

<!-- MANUAL ADDITIONS START -->
## Testing with the Android App

**Always use the app-id prefix** when sending messages to the Android app:
```bash
uv run udp-sender send -h <DEVICE_IP> -p 5000 -a "broker" -m "your message"
```

Messages without the `-a "broker"` prefix will be received but filtered out by the app's UI (it only displays messages matching its configured appId).
<!-- MANUAL ADDITIONS END -->
