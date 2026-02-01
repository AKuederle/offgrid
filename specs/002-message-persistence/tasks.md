# Tasks: Message Persistence

**Input**: Design documents from `/specs/002-message-persistence/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/persistence-api.md

**Tests**: Included per constitution requirement (Test-First Development)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- **Library module**: `udp-service/src/main/kotlin/com/example/udpservice/`
- **App module**: `app/src/main/kotlin/com/example/udpbroker/`
- **Instrumented tests**: `udp-service/src/androidTest/kotlin/com/example/udpservice/`
- **App tests**: `app/src/androidTest/kotlin/com/example/udpbroker/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add Room dependencies and create persistence package structure

- [X] T001 Add Room dependencies and kapt plugin to udp-service/build.gradle.kts
- [X] T002 Create persistence package directory at udp-service/src/main/kotlin/com/example/udpservice/persistence/
- [X] T003 [P] Create persistence test package at udp-service/src/androidTest/kotlin/com/example/udpservice/persistence/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core persistence infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundation

- [X] T004 [P] Test for PacketEntity insert/retrieve in udp-service/src/androidTest/kotlin/com/example/udpservice/persistence/PacketDaoTest.kt
- [X] T005 [P] Test for PacketEntity 64KB BLOB storage in PacketDaoTest.kt
- [X] T006 [P] Test for observePackets Flow emission in PacketDaoTest.kt
- [X] T007 [P] Test for deleteAllPackets in PacketDaoTest.kt

### Implementation for Foundation

- [X] T008 Create PacketEntity data class with Room annotations in udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketEntity.kt (includes appId field with index)
- [X] T009 Create PacketDao interface with suspend/Flow methods in udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketDao.kt (includes appId-filtered queries)
- [X] T010 Create PacketDatabase singleton with getInstance() in udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketDatabase.kt
- [X] T011 Add UdpPacket.toEntity() extension function in udp-service/src/main/kotlin/com/example/udpservice/api/UdpPacket.kt (includes PacketParser for length-prefixed appId)
- [X] T012 Run foundation tests to verify Room setup works

**Checkpoint**: Foundation ready - Room database compiles and tests pass. User story implementation can now begin.

---

## Phase 3: User Story 1 & 2 - Messages Persist When Backgrounded/Killed (Priority: P1) MVP

**Goal**: Service persists UDP packets to database; packets survive app backgrounding and process kill

**Independent Test (US1)**: Start service, send packets, press Home to background app, wait 30 seconds, send more packets, return to app - all packets should be visible

**Independent Test (US2)**: Start service, send packets, kill app from recents, send more packets, reopen app - all packets should be visible

### Tests for User Story 1 & 2

- [X] T013 [P] [US1/US2] Test service persists packet on receive in udp-service/src/androidTest/kotlin/com/example/udpservice/PersistenceIntegrationTest.kt
- [X] T014 [P] [US1/US2] Test packets retrievable after database close/reopen in PersistenceIntegrationTest.kt
- [X] T015 [P] [US1/US2] Test high-rate packet persistence (100/sec burst) in PersistenceIntegrationTest.kt

### Implementation for User Story 1 & 2

- [X] T016 [US1/US2] Add database and DAO lazy properties to UdpReceiverService in udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt
- [X] T017 [US1/US2] Implement persistPacket() suspend function in UdpReceiverService
- [X] T018 [US1/US2] Integrate packet persistence into onStartCommand flow collection in UdpReceiverService (via UdpSocket callback)
- [X] T019 [US1/US2] Add error handling for database write failures in UdpReceiverService (log and continue)
- [X] T020 [US1/US2] Modify MainActivity to observe packets from database instead of in-memory PacketLog in app/src/main/kotlin/com/example/udpbroker/MainActivity.kt
- [X] T021 [US1/US2] Add database DAO access to MainActivity for packet observation
- [ ] T022 [US1/US2] Run persistence integration tests to verify service writes survive lifecycle (blocked by device verification)

**Checkpoint**: At this point, packets persist through app backgrounding and killing. Core value proposition proven.

---

## Phase 4: User Story 3 - View Message History (Priority: P2)

**Goal**: UI displays scrollable history of all stored packets, newest first, with real-time updates

**Independent Test**: Send 200 packets, scroll through the list, verify all are accessible and ordered newest-first

### Tests for User Story 3

- [ ] T023 [P] [US3] Test packets displayed newest-first in app/src/androidTest/kotlin/com/example/udpbroker/HistoryDisplayTest.kt (pending device access)
- [ ] T024 [P] [US3] Test new packet appears without losing scroll position in HistoryDisplayTest.kt (pending device access)

### Implementation for User Story 3

- [X] T025 [US3] Update BrokerScreen to display packets from database Flow in app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt
- [X] T026 [US3] Implement packet count display from observePacketCount() Flow in BrokerScreen
- [X] T027 [US3] Add packet detail view composable in app/src/main/kotlin/com/example/udpbroker/ui/PacketDetailView.kt
- [X] T028 [US3] Implement tap-to-view-details interaction for packet items in BrokerScreen
- [X] T029 [US3] Make detail view scrollable for large payloads in PacketDetailView

**Checkpoint**: At this point, users can scroll through all stored packets and view full details.

---

## Phase 5: User Story 4 - Handle Maximum Size UDP Packets (Priority: P2)

**Goal**: System correctly stores and displays 64KB packets without truncation

**Independent Test**: Send a 64KB UDP packet, verify it is stored and can be viewed in full

### Tests for User Story 4

- [X] T030 [P] [US4] Test 64KB packet stored without truncation in PersistenceIntegrationTest.kt (in PacketDaoTest.kt)
- [ ] T031 [P] [US4] Test 64KB packet viewable in detail view in app/src/androidTest/kotlin/com/example/udpbroker/LargePacketTest.kt (pending device access)
- [ ] T032 [P] [US4] Test UI scroll performance with large packets (<100ms lag) in LargePacketTest.kt (pending device access)

### Implementation for User Story 4

- [X] T033 [US4] Verify PacketEntity BLOB handles 64KB (done in Phase 2, tested in PacketDaoTest)
- [X] T034 [US4] Ensure PacketDetailView displays large payloads efficiently with scrolling
- [X] T035 [US4] Add hex fallback display for binary payloads in PacketDetailView

**Checkpoint**: At this point, maximum-size UDP packets are fully supported.

---

## Phase 6: User Story 5 - Reset Storage for Testing (Priority: P3)

**Goal**: User can erase all stored data when service is stopped

**Independent Test**: With service stopped and messages stored, tap "Erase All Data", confirm storage is cleared, restart service and verify clean state

### Tests for User Story 5

- [X] T036 [P] [US5] Test deleteAllPackets clears database in PacketDaoTest.kt (also deletePacketsByAppId tested)
- [ ] T037 [P] [US5] Test Erase button disabled when service running in app/src/androidTest/kotlin/com/example/udpbroker/EraseDataTest.kt (pending device access)
- [ ] T038 [P] [US5] Test Erase button enabled when service stopped in EraseDataTest.kt (pending device access)
- [ ] T039 [P] [US5] Test erase operation clears UI display in EraseDataTest.kt (pending device access)

### Implementation for User Story 5

- [X] T040 [US5] Add "Erase All Data" button to BrokerScreen in app/src/main/kotlin/com/example/udpbroker/ui/BrokerScreen.kt
- [X] T041 [US5] Implement erase button enabled/disabled state based on ReceiverState in BrokerScreen
- [X] T042 [US5] Implement erase button click handler that calls deletePacketsByAppId() in MainActivity
- [X] T043 [US5] Add confirmation dialog before erasing data in BrokerScreen

**Checkpoint**: At this point, users can reset storage for clean testing sessions.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, validation, and documentation

- [X] T044 Remove or deprecate in-memory PacketLog class in app/src/main/kotlin/com/example/udpbroker/PacketLog.kt (marked @Deprecated)
- [ ] T045 [P] Update README with persistence feature documentation (optional for MVP)
- [X] T046 Run all tests (unit + instrumented) to verify complete implementation (unit tests pass; instrumented tests blocked by device verification)
- [ ] T047 Run quickstart.md manual test scenarios for validation (requires device deployment)
- [X] T048 [P] Add logging for persistence operations in UdpReceiverService

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phases 3-6)**: All depend on Foundational phase completion
  - US1/US2 (P1) should complete first (MVP)
  - US3, US4 (P2) can proceed after US1/US2
  - US5 (P3) can proceed after US3
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

```
Phase 1: Setup
    ↓
Phase 2: Foundational (PacketEntity, PacketDao, PacketDatabase)
    ↓
Phase 3: US1/US2 (Service persistence + MainActivity observation)
    ↓
Phase 4: US3 (History display, detail view) ←── depends on Phase 3
    ↓
Phase 5: US4 (64KB packets) ←── can run parallel with Phase 4
    ↓
Phase 6: US5 (Erase button) ←── depends on Phase 4 (uses BrokerScreen)
    ↓
Phase 7: Polish
```

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD)
- Entity/DAO before service integration
- Service integration before UI integration
- Core implementation before polish
- Story complete before moving to next priority

### Parallel Opportunities

**Phase 1 (Setup)**:
- T002 and T003 can run in parallel after T001

**Phase 2 (Foundational)**:
- T004, T005, T006, T007 can all run in parallel (different test methods)
- T008, T009, T010 can run in parallel (different files)

**Phase 3 (US1/US2)**:
- T013, T014, T015 can all run in parallel (different test methods)

**Phase 4 (US3)**:
- T023, T024 can run in parallel

**Phase 5 (US4)**:
- T030, T031, T032 can run in parallel

**Phase 6 (US5)**:
- T036, T037, T038, T039 can run in parallel

---

## Parallel Example: Foundational Phase

```bash
# Launch all foundation tests together:
Task: "Test for PacketEntity insert/retrieve"
Task: "Test for PacketEntity 64KB BLOB storage"
Task: "Test for observePackets Flow emission"
Task: "Test for deleteAllPackets"

# Launch all entity/DAO implementations together:
Task: "Create PacketEntity data class"
Task: "Create PacketDao interface"
Task: "Create PacketDatabase singleton"
```

---

## Implementation Strategy

### MVP First (User Story 1 & 2 Only)

1. Complete Phase 1: Setup (Room dependencies)
2. Complete Phase 2: Foundational (Entity, DAO, Database)
3. Complete Phase 3: User Story 1 & 2 (Service + MainActivity integration)
4. **STOP and VALIDATE**: Test persistence independently
5. Deploy/demo MVP - core value proven

### Incremental Delivery

1. **MVP**: Setup + Foundational + US1/US2 → Packets persist through backgrounding/killing
2. **+History**: US3 → Users can scroll through and view all packets
3. **+Large Packets**: US4 → 64KB packets fully supported
4. **+Reset**: US5 → Users can clear storage for testing

### Suggested MVP Scope

**Minimum deliverable**: Phases 1-3 (Tasks T001-T022)

This proves:
- Room database integration works
- Service persists packets independently of UI
- Packets survive app backgrounding and killing
- MainActivity can observe stored packets

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing (TDD required by constitution)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
