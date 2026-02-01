# android-udp-service Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-01

## Active Technologies
- Kotlin 1.9.22 + Jetpack Compose, Coroutines 1.7.3, Room 2.6.1 (002-message-persistence)
- Room database with appId-indexed packets for multi-app filtering (002-message-persistence)

- Kotlin 1.9.x, Python 3.10+ (001-udp-receiver-mvp)

## Project Structure

```text
app/                      # Android app module
udp-service/              # UDP service library module
tools/udp-sender/         # Python test tool
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
./gradlew :udp-service:test
./gradlew :app:test

# Run on-device tests (requires connected device)
./gradlew :app:connectedAndroidTest

# Python tool
cd tools/udp-sender && uv run udp-sender --help
cd tools/udp-sender && uv run pytest
cd tools/udp-sender && ruff check .
```

## Code Style

Kotlin 1.9.x, Python 3.10+: Follow standard conventions

## Recent Changes
- 002-message-persistence: Added Room 2.6.1 persistence with appId prefix filtering, packet detail view, erase functionality

- 001-udp-receiver-mvp: Added Kotlin 1.9.x, Python 3.10+

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
