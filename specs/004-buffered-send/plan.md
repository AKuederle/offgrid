# Implementation Plan: Buffered Send with Persistent Retry

**Branch**: `004-buffered-send` | **Date**: 2026-02-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-buffered-send/spec.md`

## Summary

Add application-level persistence and retry for outbound messages. Messages are stored in Room database before transmission, with exponential backoff retry (~60s total), automatic resume when peer activity is detected, and a presence broadcast protocol for peer discovery. Additionally, retire the Python CLI tool and replace it with a Kotlin CLI that wraps the reliable-udp library.

## Technical Context

**Language/Version**: Kotlin 1.9.22, JVM 17
**Primary Dependencies**: Kotlin Coroutines 1.7.3, Room 2.6.1, reliable-udp library
**Storage**: Room database (extending existing schema from 002-message-persistence)
**Testing**: JUnit 5 + MockK (unit), AndroidX Test (instrumented)
**Target Platform**: Android 10+ (API 29), plus Kotlin JVM for CLI tool
**Project Type**: Multi-module Android + JVM library
**Performance Goals**: <3s delivery under normal conditions, 100+ pending messages per peer
**Constraints**: Offline-capable, no cloud dependencies, local network only
**Scale/Scope**: Multiple peers, ~1000 messages per peer max

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | PASS | All components will have unit tests before implementation |
| II. Library-First Architecture | PASS | Send queue manager will be in udp-service library, Kotlin CLI in new module |
| III. Local-First Design | PASS | All storage in Room, no cloud dependencies |
| IV. Simplicity and YAGNI | PASS | Building only what spec requires |
| V. Clean Abstractions | PASS | Interfaces for SendQueue, RetryScheduler; DI throughout |

**Platform Requirements**:
- Minimum SDK API 29: PASS
- Kotlin with Coroutines: PASS
- Gradle multi-module: PASS (adding new CLI module)
- Room for storage: PASS

**Testing Requirements**:
- Stage 1 (Unit): Pure Kotlin tests for retry logic, queue management
- Stage 2 (Emulator): Service lifecycle, database persistence
- Stage 3 (Device): Real network presence broadcast, WiFi IP detection

## Project Structure

### Documentation (this feature)

```text
specs/004-buffered-send/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (from /speckit.tasks)
```

### Source Code (repository root)

```text
# Existing modules (modified)
udp-service/
├── src/main/kotlin/com/example/udpservice/
│   ├── send/                    # NEW: Send queue management
│   │   ├── OutboundMessage.kt   # Entity for pending messages
│   │   ├── SendQueue.kt         # Interface for queue operations
│   │   ├── SendQueueImpl.kt     # Room-backed implementation
│   │   ├── RetryScheduler.kt    # Exponential backoff logic
│   │   └── DeliveryTracker.kt   # Status updates and callbacks
│   ├── presence/                # NEW: Presence protocol
│   │   ├── PresenceMessage.kt   # Lightweight ping message
│   │   └── PresenceBroadcaster.kt # UDP broadcast sender
│   └── db/                      # Existing, extended
│       └── OutboundMessageDao.kt # NEW: DAO for outbound messages
└── src/test/kotlin/             # Unit tests

reliable-udp/
└── (no changes needed - already supports required functionality)

# New module
udp-cli/                         # NEW: Kotlin CLI tool
├── build.gradle.kts
├── src/main/kotlin/com/example/udpcli/
│   ├── Main.kt                  # CLI entry point
│   ├── commands/
│   │   ├── SendCommand.kt
│   │   ├── ReceiveCommand.kt
│   │   └── PresenceCommand.kt
│   └── UdpClient.kt             # Wraps reliable-udp
└── src/test/kotlin/             # CLI unit tests

# Removed
tools/udp-sender/                # REMOVED: Python tool (deprecated)
```

**Structure Decision**: Multi-module Android project with new JVM-only CLI module. The udp-service module is extended with send queue functionality. The Python tool is removed and replaced with udp-cli module.

## Complexity Tracking

No complexity violations - design follows constitution principles.
