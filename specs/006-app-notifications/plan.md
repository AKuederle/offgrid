# Implementation Plan: App Prefix Notifications with Deep Linking

**Branch**: `006-app-notifications` | **Date**: 2026-02-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-app-notifications/spec.md`

## Summary

Implement a **broker-broadcast architecture** where the broker app stores messages and sends explicit broadcasts to registered client packages. Client apps (including the broker app itself for testing) handle their own notifications via BroadcastReceiver + WorkManager.

**Key Components**:
1. **Broker**: Persist messages → send explicit broadcast with `setPackage()`
2. **Client**: BroadcastReceiver → foreground: immediate pull / background: WorkManager → notification
3. **Test**: Broker broadcasts to itself, handles both prefixes ("broker", "alerts")

## Technical Context

**Language/Version**: Kotlin 1.9.22 with Coroutines 1.7.3
**Primary Dependencies**: Jetpack Compose + Material3, Room 2.6.1, WorkManager, AndroidX Core (notifications)
**Storage**: Room database (existing `packet_database.db`, schema version 2)
**Testing**: JUnit 5 + MockK (unit), Instrumented tests (Android)
**Target Platform**: Android API 29+ (Android 10+)
**Project Type**: Multi-module Android (app + udp-service + reliable-udp)
**Performance Goals**: Broadcasts within 100ms, notifications within 2s, UI updates within 500ms
**Constraints**: Offline-capable (local network only), no cloud dependencies
**Scale/Scope**: 2 test prefixes, designed for future multi-app support

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | **PASS** | TDD for all components |
| II. Library-First Architecture | **PASS** | Broadcast logic in `udp-service`, notification handling in `app` |
| III. Local-First Design | **PASS** | No cloud dependencies; explicit local broadcasts |
| IV. Simplicity and YAGNI | **PASS** | Minimal broadcast (ping only); client pulls when ready |
| V. Clean Abstractions | **PASS** | Clear separation: broker broadcasts, client handles notifications |

**Testing Stages**:
- Stage 1 (Unit): Broadcast sending logic, WorkManager worker, notification builder
- Stage 2 (Emulator): BroadcastReceiver integration, deep link navigation, database ops
- Stage 3 (Device): Real notification interaction, background/foreground transitions

## Project Structure

### Documentation (this feature)

```text
specs/006-app-notifications/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (internal APIs)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
udp-service/
├── src/main/kotlin/com/example/udpservice/
│   ├── UdpReceiverService.kt        # MODIFY: Add broadcast after persist
│   ├── UdpSocket.kt                 # MODIFY: Callback includes broadcast trigger
│   ├── broadcast/
│   │   ├── MessageBroadcaster.kt    # NEW: Sends explicit broadcasts
│   │   └── BroadcastActions.kt      # NEW: Action constants
│   ├── registration/
│   │   ├── AppRegistration.kt       # NEW: Registration data class
│   │   ├── RegistrationRepository.kt # NEW: Storage interface
│   │   └── RegistrationRepositoryImpl.kt # NEW: Room implementation
│   ├── persistence/
│   │   ├── PacketEntity.kt          # MODIFY: Add isRead field
│   │   ├── PacketDao.kt             # MODIFY: Add unread queries, mark as read
│   │   ├── AppRegistrationEntity.kt # NEW: Registration entity
│   │   ├── AppRegistrationDao.kt    # NEW: Registration DAO
│   │   └── PacketDatabase.kt        # MODIFY: Add entities, bump version
│   └── api/
│       └── UdpReceiver.kt           # MODIFY: Registration includes package name
└── src/test/                        # Unit tests

app/
├── src/main/kotlin/com/example/udpbroker/
│   ├── MainActivity.kt              # MODIFY: Deep link handling, dual registration
│   ├── receiver/
│   │   └── MessageReceiver.kt       # NEW: BroadcastReceiver for NEW_MESSAGE
│   ├── worker/
│   │   └── MessageNotificationWorker.kt # NEW: WorkManager worker for background
│   ├── notification/
│   │   ├── NotificationHelper.kt    # NEW: Build and show notifications
│   │   └── NotificationChannels.kt  # NEW: Per-prefix channel management
│   ├── ui/
│   │   ├── BrokerApp.kt             # NEW: Navigation host with tabs
│   │   ├── MessagesScreen.kt        # NEW: Per-prefix message list (marks as read)
│   │   ├── PacketDetailView.kt      # Existing: no changes
│   │   └── navigation/
│   │       └── AppNavigation.kt     # NEW: Deep link route definitions
│   └── ...
├── src/main/AndroidManifest.xml     # MODIFY: Add receiver, deep link intent filter
└── src/test/                        # Unit tests
└── src/androidTest/                 # Instrumented tests
```

**Structure Decision**:
- Broadcast sending in `udp-service` (library) - reusable for any client
- Broadcast receiving + notifications in `app` - client-specific logic
- WorkManager for background processing - Android best practice

## Complexity Tracking

No constitution violations. Design follows all principles:
- Clear separation: broker broadcasts, client handles notifications
- Minimal broadcast: just a ping, client pulls data
- Future-proof: same mechanism works for external apps

## Phase 0: Research

See [research.md](./research.md) for:
- Explicit broadcasts with setPackage()
- WorkManager for background notification processing
- BroadcastReceiver lifecycle and constraints
- Deep link handling in Jetpack Compose Navigation
- Room database migration strategy

## Phase 1: Design

See:
- [data-model.md](./data-model.md) - Entity definitions and relationships
- [contracts/](./contracts/) - Internal API interfaces
- [quickstart.md](./quickstart.md) - Development setup and testing commands
