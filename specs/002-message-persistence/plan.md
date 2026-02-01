# Implementation Plan: Message Persistence

**Branch**: `002-message-persistence` | **Date**: 2026-02-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-message-persistence/spec.md`

## Summary

Implement Room database persistence for received UDP packets so messages survive app backgrounding and process termination. The foreground service will write packets directly to Room storage, and the UI will observe the database via Flow. This proves the service can capture network traffic independently of the UI lifecycle.

## Technical Context

**Language/Version**: Kotlin 1.9.22
**Primary Dependencies**: Jetpack Compose, Coroutines 1.7.3, Room (to be added)
**Storage**: Room database (SQLite under the hood)
**Testing**: JUnit 5 + MockK (unit), AndroidX Test (instrumented)
**Target Platform**: Android API 29+ (Android 10+)
**Project Type**: Mobile (multi-module: `app` + `udp-service` library)
**Performance Goals**: <500ms history load, <50ms per write, 60fps UI scrolling
**Constraints**: Async writes (no blocking packet reception), offline-capable, max 64KB per packet
**Scale/Scope**: 10,000+ packets, single device, single user

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Test-First Development** | ✅ PASS | Plan includes TDD approach - tests first for Room DAOs, repository, and integration |
| **II. Library-First Architecture** | ✅ PASS | Persistence layer will be in `udp-service` module with interface-based design |
| **III. Local-First Design** | ✅ PASS | Room database is local storage, no cloud dependencies |
| **IV. Simplicity and YAGNI** | ✅ PASS | Single table, direct DAO access, no repository pattern unless needed |
| **V. Clean Abstractions** | ✅ PASS | DAO interface, Flow-based observation, DI-ready design |

**Platform Requirements Check**:
- ✅ Minimum SDK 29
- ✅ Kotlin with Coroutines
- ✅ Gradle multi-module
- ✅ Room for structured local storage (constitution requirement)
- ✅ Lifecycle-aware components (Flow collection)

**Testing Stages**:
- Stage 1: Unit tests for DAO with in-memory Room database
- Stage 2: Emulator tests for persistence across app lifecycle
- Stage 3: Device-only tests if needed (likely N/A for this feature)

## Project Structure

### Documentation (this feature)

```text
specs/002-message-persistence/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (internal API contracts)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
udp-service/                              # Library module (persistence lives here)
├── src/main/kotlin/com/example/udpservice/
│   ├── api/
│   │   ├── UdpPacket.kt                 # Existing - no changes
│   │   └── ReceiverState.kt             # Existing - no changes
│   ├── persistence/                      # NEW: Persistence layer
│   │   ├── PacketDatabase.kt            # Room database definition
│   │   ├── PacketEntity.kt              # Room entity (stored packet)
│   │   ├── PacketDao.kt                 # Data Access Object interface
│   │   └── PacketRepository.kt          # Repository interface + impl (if needed)
│   ├── UdpReceiverService.kt            # Modify: inject database, write on receive
│   ├── UdpSocket.kt                     # Existing - no changes
│   ├── UdpReceiver.kt                   # Existing - no changes
│   └── ...
├── src/test/kotlin/                      # Unit tests (JVM)
│   └── com/example/udpservice/persistence/
│       └── PacketDaoTest.kt             # DAO tests with in-memory DB
└── src/androidTest/kotlin/               # Instrumented tests
    └── com/example/udpservice/
        └── PersistenceIntegrationTest.kt # Full lifecycle tests

app/                                      # Application module
├── src/main/kotlin/com/example/udpbroker/
│   ├── MainActivity.kt                  # Modify: load from DB, observe Flow
│   ├── PacketLog.kt                     # Modify: replace in-memory with DB-backed
│   └── ui/
│       └── BrokerScreen.kt              # Modify: add "Erase All Data" button
└── src/androidTest/kotlin/               # App-level integration tests
    └── com/example/udpbroker/
        └── PersistenceE2ETest.kt        # End-to-end persistence scenarios
```

**Structure Decision**: Persistence layer goes in `udp-service` module because:
1. Service writes packets directly (service lives in udp-service)
2. Library-first architecture principle (self-contained module)
3. Could be reused if another app consumes the library

## Complexity Tracking

> **No violations requiring justification.**

The design follows all constitution principles:
- Single Room table (simplest viable solution)
- Direct DAO access (no unnecessary Repository abstraction unless testing requires it)
- Interface-based design (DAO is already an interface)
- Async writes via Room's suspend functions + Coroutines

---

## Post-Design Constitution Re-Check

*Performed after Phase 1 design completion.*

| Principle | Status | Validation |
|-----------|--------|------------|
| **I. Test-First Development** | ✅ PASS | Tests defined in data-model.md, instrumented test plan in quickstart.md |
| **II. Library-First Architecture** | ✅ PASS | Persistence layer in `udp-service` module, reusable by other consumers |
| **III. Local-First Design** | ✅ PASS | Room/SQLite is local storage, no cloud or network dependencies |
| **IV. Simplicity and YAGNI** | ✅ PASS | Single table, direct DAO, no Repository wrapper, no pagination (add later if needed) |
| **V. Clean Abstractions** | ✅ PASS | PacketDao interface, Flow-based observation, singleton via companion object |

**Testing Stage Compliance**:
- Stage 1 (Unit/JVM): PacketEntity validation, UdpPacket.toEntity() conversion
- Stage 2 (Emulator): PacketDaoTest with in-memory database, persistence lifecycle tests
- Stage 3 (Device): Not required for this feature

**All gates passed. Ready for task generation.**

---

## Generated Artifacts

| Artifact | Status | Location |
|----------|--------|----------|
| `plan.md` | ✅ Complete | This file |
| `research.md` | ✅ Complete | `specs/002-message-persistence/research.md` |
| `data-model.md` | ✅ Complete | `specs/002-message-persistence/data-model.md` |
| `quickstart.md` | ✅ Complete | `specs/002-message-persistence/quickstart.md` |
| `contracts/persistence-api.md` | ✅ Complete | `specs/002-message-persistence/contracts/persistence-api.md` |
| `tasks.md` | ⏳ Pending | Run `/speckit.tasks` to generate |

---

## Next Steps

Run `/speckit.tasks` to generate the implementation task list based on this plan.
