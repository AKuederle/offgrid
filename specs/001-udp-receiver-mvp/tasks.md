# Tasks: UDP Receiver MVP

**Input**: Design documents from `/specs/001-udp-receiver-mvp/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: TDD is REQUIRED per project constitution - tests MUST be written and FAIL before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, etc.)
- Include exact file paths in descriptions

## Path Conventions

- **Android app**: `app/src/main/kotlin/com/example/udpbroker/`
- **Android library**: `udp-service/src/main/kotlin/com/example/udpservice/`
- **Unit tests**: `*/src/test/kotlin/`
- **On-device tests**: `*/src/androidTest/kotlin/`
- **Python tool**: `tools/udp-sender/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize multi-module Gradle project and Python tool structure

- [X] T001 Create root `settings.gradle.kts` with module includes (`:app`, `:udp-service`)
- [X] T002 Create root `build.gradle.kts` with Android Gradle Plugin and Kotlin plugin declarations
- [X] T003 [P] Create `gradle.properties` with JVM args and AndroidX configuration
- [X] T004 [P] Create `udp-service/build.gradle.kts` with library plugin, Kotlin, Coroutines dependencies
- [X] T005 [P] Create `app/build.gradle.kts` with application plugin, Compose dependencies, dependency on `:udp-service`
- [X] T006 [P] Create `tools/udp-sender/pyproject.toml` with click dependency and entry point
- [X] T007 [P] Create `tools/udp-sender/src/udp_sender/__init__.py` (empty module init)
- [X] T008 Verify project builds with `./gradlew projects` showing both modules

**Checkpoint**: Project skeleton compiles, Python tool structure exists

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data classes and interfaces that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T009 [P] Unit test for UdpPacket.displayText UTF-8 decoding in `udp-service/src/test/kotlin/com/example/udpservice/api/UdpPacketTest.kt`
- [X] T010 [P] Unit test for UdpPacket.displayText hex fallback in `udp-service/src/test/kotlin/com/example/udpservice/api/UdpPacketTest.kt`
- [X] T011 [P] Unit test for UdpPacket equals/hashCode with ByteArray in `udp-service/src/test/kotlin/com/example/udpservice/api/UdpPacketTest.kt`
- [X] T012 [P] Unit test for ReceiverState sealed interface variants in `udp-service/src/test/kotlin/com/example/udpservice/api/ReceiverStateTest.kt`

### Implementation

- [X] T013 Create UdpPacket data class in `udp-service/src/main/kotlin/com/example/udpservice/api/UdpPacket.kt` with data, sourceAddress, timestamp, displayText
- [X] T014 [P] Create ReceiverState sealed interface in `udp-service/src/main/kotlin/com/example/udpservice/api/ReceiverState.kt` with Stopped, Starting, Running, Error
- [X] T015 [P] Create UdpReceiver interface in `udp-service/src/main/kotlin/com/example/udpservice/UdpReceiver.kt` per contracts/udp-service-api.md
- [X] T016 Create library AndroidManifest.xml in `udp-service/src/main/AndroidManifest.xml` with permissions (INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE)
- [X] T017 Verify tests pass: `./gradlew :udp-service:test`

**Checkpoint**: Foundation ready - UdpPacket, ReceiverState, UdpReceiver interface all tested and implemented

---

## Phase 3: User Story 1 - Start UDP Listener (Priority: P1) 🎯 MVP

**Goal**: User can start the foreground service from the app and see a notification

**Independent Test**: Launch app, tap "Start Service", verify notification appears with port number

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T018 Unit test for UdpSocket state transitions (Stopped→Starting→Running) in `udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt`
- [X] T019 [P] Unit test for UdpSocket stop() releases resources in `udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt`
- [ ] T020 [P] On-device test for foreground service notification in `app/src/androidTest/kotlin/com/example/udpbroker/ServiceNotificationTest.kt`

### Implementation

- [X] T021 [US1] Implement UdpSocket class in `udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt` with SharedFlow, IO dispatcher, state management
- [X] T022 [US1] Create UdpReceiverService foreground service in `udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt` with notification channel, LocalBinder
- [X] T023 [US1] Register service in `udp-service/src/main/AndroidManifest.xml` with foregroundServiceType="specialUse" and PROPERTY_SPECIAL_USE_FGS_SUBTYPE
- [X] T024 [US1] Create app AndroidManifest.xml in `app/src/main/AndroidManifest.xml` with MainActivity, service reference
- [X] T025 [US1] Create Compose theme in `app/src/main/kotlin/com/example/udpbroker/ui/theme/` (Theme.kt, Color.kt, Type.kt)
- [X] T026 [US1] Create BrokerScreen composable in `app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt` with Start button and status display
- [X] T027 [US1] Create MainActivity in `app/src/main/kotlin/com/example/udpbroker/MainActivity.kt` with service binding, permission request (POST_NOTIFICATIONS)
- [X] T028 [US1] Add strings.xml in `app/src/main/res/values/strings.xml`
- [X] T029 [US1] Verify tests pass: `./gradlew :udp-service:test :app:test`
- [ ] T030 [US1] Verify on-device: `./gradlew :app:connectedAndroidTest` (TODO: requires physical device)

**Checkpoint**: User Story 1 complete - app installs, service starts, notification appears

---

## Phase 4: User Story 2 - Receive UDP Packet (Priority: P1)

**Goal**: Packets sent to the device appear in the app's message log

**Independent Test**: With service running, send packet via Python tool, see it in the UI

**Depends on**: US1 (service must be running), US3 (need sender tool - can test with netcat initially)

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T031 Unit test for UdpSocket packet emission via Flow in `udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt`
- [X] T032 [P] Unit test for packet log max 100 entries in `app/src/test/kotlin/com/example/udpbroker/PacketLogTest.kt`
- [X] T033 [P] On-device test for UDP packet reception in `udp-service/src/androidTest/kotlin/com/example/udpservice/UdpReceptionTest.kt`

### Implementation

- [X] T034 [US2] Add packet reception loop to UdpSocket in `udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt`
- [X] T035 [US2] Expose packets Flow from UdpReceiverService via LocalBinder in `udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt`
- [X] T036 [US2] Create PacketLog state holder in `app/src/main/kotlin/com/example/udpbroker/PacketLog.kt` (max 100 entries, newest first)
- [X] T037 [US2] Add packet list UI to BrokerScreen in `app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt` (LazyColumn, packet display)
- [X] T038 [US2] Wire packet Flow collection in MainActivity in `app/src/main/kotlin/com/example/udpbroker/MainActivity.kt`
- [X] T039 [US2] Verify tests pass: `./gradlew :udp-service:test :app:test`
- [ ] T040 [US2] Verify on-device: `./gradlew :udp-service:connectedAndroidTest`

**Checkpoint**: User Story 2 complete - packets appear in UI within 1 second

---

## Phase 5: User Story 3 - Send Test Packets with Python Tool (Priority: P1)

**Goal**: Developer can send UDP packets using a simple CLI tool

**Independent Test**: Run `uv run udp-sender send -h <ip> -m "hello"` and verify output

**No dependencies on other user stories** - can be done in parallel with US1/US2

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T041 [P] Pytest for send command in `tools/udp-sender/tests/test_cli.py`
- [X] T042 [P] Pytest for flood command in `tools/udp-sender/tests/test_cli.py`
- [X] T043 [P] Pytest for UdpSender class send method in `tools/udp-sender/tests/test_sender.py`

### Implementation

- [X] T044 [P] [US3] Create UdpSender class in `tools/udp-sender/src/udp_sender/sender.py` with send(), flood() methods
- [X] T045 [US3] Create CLI with click in `tools/udp-sender/src/udp_sender/cli.py` (main group, send, flood, interactive commands)
- [X] T046 [US3] Verify tool works: `cd tools/udp-sender && uv run udp-sender --help`
- [X] T047 [US3] Verify tests pass: `cd tools/udp-sender && uv run pytest`

**Checkpoint**: User Story 3 complete - Python tool sends packets successfully

---

## Phase 6: User Story 4 - View Network Info (Priority: P2)

**Goal**: User can see device IP and port to know where to send packets

**Independent Test**: Start service, verify UI shows valid local IP (not 127.0.0.1) and port

**Depends on**: US1 (service must be running)

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T048 Unit test for getLocalIpAddresses() filtering in `udp-service/src/test/kotlin/com/example/udpservice/NetworkUtilsTest.kt`
- [X] T049 [P] On-device test for IP address display in `udp-service/src/androidTest/kotlin/com/example/udpservice/NetworkInfoDeviceTest.kt` (@DeviceOnly)

### Implementation

- [X] T050 [US4] Create NetworkUtils.kt in `udp-service/src/main/kotlin/com/example/udpservice/NetworkUtils.kt` with getLocalIpAddresses()
- [X] T051 [US4] Add addresses to ReceiverState.Running in `udp-service/src/main/kotlin/com/example/udpservice/api/ReceiverState.kt`
- [X] T052 [US4] Update BrokerScreen to display IP and port in `app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt`
- [X] T053 [US4] Verify tests pass: `./gradlew :udp-service:test :app:test`

**Checkpoint**: User Story 4 complete - IP and port visible in running state

---

## Phase 7: User Story 5 - Stop UDP Listener (Priority: P2)

**Goal**: User can stop the service to release the port

**Independent Test**: With service running, tap "Stop", verify notification disappears

**Depends on**: US1 (service must exist to stop)

### Tests (TDD - Write First, Ensure They FAIL)

- [X] T054 Unit test for UdpSocket stop() state transition in `udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt`
- [ ] T055 [P] On-device test for service stop in `app/src/androidTest/kotlin/com/example/udpbroker/ServiceStopTest.kt`

### Implementation

- [X] T056 [US5] Implement stop() in UdpSocket in `udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt` (cancel job, close socket)
- [X] T057 [US5] Add stop button to BrokerScreen in `app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt`
- [X] T058 [US5] Wire stop action in MainActivity in `app/src/main/kotlin/com/example/udpbroker/MainActivity.kt`
- [ ] T059 [US5] Verify tests pass and on-device: `./gradlew :app:connectedAndroidTest`

**Checkpoint**: User Story 5 complete - service stops cleanly

---

## Phase 8: User Story 6 - Follow Setup and Testing Guide (Priority: P2)

**Goal**: Clear documentation enables new developers to build and test

**Independent Test**: Follow README from scratch, successfully send/receive a packet

**Depends on**: US1, US2, US3 (need working app and tool to document)

### Implementation

- [ ] T060 [US6] Create README.md at repository root with Prerequisites section
- [ ] T061 [US6] Add Build and Install section to README.md (Gradle commands, adb install)
- [ ] T062 [US6] Add Testing section to README.md (finding device IP, using udp-sender)
- [ ] T063 [US6] Add Python Tool section to README.md (send, flood, interactive modes)
- [ ] T064 [US6] Add Development section to README.md (TDD workflow, test commands)
- [ ] T065 [US6] Validate README by following it on the current setup

**Checkpoint**: User Story 6 complete - documentation enables onboarding

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements and validation

- [ ] T066 Run full test suite: `./gradlew test connectedAndroidTest`
- [ ] T067 [P] Add error handling for port already in use in UdpSocket
- [ ] T068 [P] Add error state display in BrokerScreen
- [ ] T069 Validate against quickstart.md workflow
- [ ] T070 Final code review and cleanup

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup) ──────────────────────────────────────────────┐
                                                              │
Phase 2 (Foundational) ◄──────────────────────────────────────┘
    │
    ├──► Phase 3 (US1: Start Listener) ──────► Phase 7 (US5: Stop)
    │        │
    │        └──► Phase 4 (US2: Receive Packet) ──► Phase 8 (US6: Docs)
    │                                                    │
    ├──► Phase 5 (US3: Python Tool) ─────────────────────┘
    │
    └──► Phase 6 (US4: Network Info)

Phase 9 (Polish) ◄── All user stories complete
```

### User Story Dependencies

| Story | Can Start After | Notes |
|-------|-----------------|-------|
| US1 (Start) | Phase 2 | No other story dependencies |
| US2 (Receive) | US1 | Needs running service |
| US3 (Python) | Phase 2 | Independent - can parallel with US1/US2 |
| US4 (Network) | US1 | Needs running service |
| US5 (Stop) | US1 | Needs service to stop |
| US6 (Docs) | US1, US2, US3 | Needs working system to document |

### Parallel Opportunities

**Within Setup (Phase 1)**:
```
T003, T004, T005, T006, T007 can all run in parallel
```

**Within Foundational (Phase 2)**:
```
T009, T010, T011, T012 (tests) can all run in parallel
T014, T015 (interfaces) can run in parallel after tests
```

**Across User Stories (after Phase 2)**:
```
US1 + US3 can run in parallel (different codebases)
US4 can start once US1 is partially done (needs service)
```

---

## Parallel Example: Foundational Tests

```bash
# Launch all foundational tests together:
Task: "Unit test for UdpPacket.displayText UTF-8 decoding"
Task: "Unit test for UdpPacket.displayText hex fallback"
Task: "Unit test for UdpPacket equals/hashCode"
Task: "Unit test for ReceiverState sealed interface"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 (Start Listener)
4. Complete Phase 4: User Story 2 (Receive Packet)
5. Complete Phase 5: User Story 3 (Python Tool)
6. **STOP and VALIDATE**: Test end-to-end with Python tool → Android app
7. MVP is functional at this point!

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1 → App starts service (minimal demo possible)
3. Add US2 → Packets visible in app (core value delivered)
4. Add US3 → Easy testing with Python tool (developer experience)
5. Add US4, US5 → Polish (network info, clean stop)
6. Add US6 → Documentation (onboarding)

---

## Task Summary

| Phase | Tasks | Parallel Tasks |
|-------|-------|----------------|
| 1. Setup | 8 | 5 |
| 2. Foundational | 9 | 6 |
| 3. US1 - Start | 13 | 2 |
| 4. US2 - Receive | 10 | 3 |
| 5. US3 - Python | 7 | 4 |
| 6. US4 - Network | 6 | 1 |
| 7. US5 - Stop | 6 | 1 |
| 8. US6 - Docs | 6 | 0 |
| 9. Polish | 5 | 2 |
| **Total** | **70** | **24** |

---

## Notes

- TDD is REQUIRED per constitution - all implementation tasks have preceding test tasks
- [P] tasks = different files, no dependencies within the phase
- [USn] label maps task to specific user story
- Each user story checkpoint validates independent functionality
- Commit after each task or logical group
- Stop at any checkpoint to validate progress
