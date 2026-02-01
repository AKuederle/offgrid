# Implementation Plan: UDP Receiver MVP

**Branch**: `001-udp-receiver-mvp` | **Date**: 2026-02-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-udp-receiver-mvp/spec.md`

## Summary

Build a minimal Android app with a foreground service that receives UDP packets over the local network, along with a Python test tool and README documentation. This proves core UDP reception works on real hardware before adding complexity. Implementation follows TDD with tests written before production code.

## Technical Context

**Language/Version**: Kotlin 1.9.x, Python 3.10+
**Primary Dependencies**:
- Android: Jetpack Compose, Coroutines/Flow, AndroidX Core
- Python: click (CLI framework)
**Storage**: In-memory only (no persistence in MVP)
**Testing**:
- Android: JUnit 5 + MockK (unit), AndroidX Test (on-device)
- Python: pytest
**Target Platform**: Android 10+ (API 29+), physical device via adb
**Project Type**: Mobile (multi-module Gradle) + Python tool
**Performance Goals**: Packet appears in UI within 1 second, handle 100 packets/sec
**Constraints**: Must work offline (local network only), foreground service required
**Scale/Scope**: Single user/developer testing tool, ~10 screens worth of UI (1 screen)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | **CONFLICT** | Spec says "manual testing only" but constitution is NON-NEGOTIABLE on TDD |
| II. Library-First Architecture | PASS | UDP socket logic will be in separate library module |
| III. Local-First Design | PASS | Core feature is local network UDP - no cloud dependencies |
| IV. Simplicity and YAGNI | PASS | MVP scope is minimal, no over-engineering |
| V. Clean Abstractions | PASS | Will use interfaces for socket/service, DI for testability |

### Gate Resolution: Test-First Development Conflict

**Decision**: Follow the constitution. TDD is non-negotiable.

The spec's "manual testing only" was written before constitution was considered. The constitution explicitly states TDD is NON-NEGOTIABLE and provides clear guidance:
- Unit tests in `test/` for pure Kotlin logic (JVM)
- On-device tests in `androidTest/` for Android-specific behavior

**Adjustment**: Implementation will include:
- Unit tests for UDP socket wrapper logic (parsing, buffering)
- Unit tests for service state management
- On-device tests for foreground service lifecycle and actual UDP reception
- Python tool tests with pytest

This aligns with constitution's "Tests MUST be written before implementation code."

## Project Structure

### Documentation (this feature)

```text
specs/001-udp-receiver-mvp/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (minimal for MVP)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
# Multi-module Android + Python tool

app/                           # Standalone broker app
├── src/
│   └── main/
│       ├── kotlin/com/example/udpbroker/
│       │   ├── MainActivity.kt
│       │   └── ui/
│       │       ├── BrokerScreen.kt
│       │       └── theme/
│       ├── res/
│       └── AndroidManifest.xml
├── src/test/                  # Unit tests (JVM)
└── src/androidTest/           # On-device tests

udp-service/                   # Reusable library module
├── src/
│   └── main/
│       ├── kotlin/com/example/udpservice/
│       │   ├── UdpSocket.kt           # Coroutine-based socket wrapper
│       │   ├── UdpReceiverService.kt  # Foreground service
│       │   └── api/
│       │       └── UdpPacket.kt       # Data class for received packets
│       └── AndroidManifest.xml
├── src/test/                  # Unit tests (JVM)
└── src/androidTest/           # On-device tests

tools/udp-sender/              # Python test tool
├── pyproject.toml
├── src/udp_sender/
│   ├── __init__.py
│   ├── cli.py
│   └── sender.py
└── tests/

# Root build files
settings.gradle.kts
build.gradle.kts
gradle.properties
README.md
```

**Structure Decision**: Multi-module Gradle project with `:udp-service` library and `:app` application. This follows Library-First Architecture (Principle II) - UDP logic is reusable. Python tool is a separate project in `tools/`.

## Complexity Tracking

> No violations requiring justification. All constitution checks pass after gate resolution.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | - | - |
