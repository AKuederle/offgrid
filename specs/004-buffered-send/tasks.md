# Tasks: Buffered Send with Persistent Retry

**Input**: Design documents from `/specs/004-buffered-send/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Following TDD per constitution - tests written first.

**Organization**: Tasks grouped by user story for independent implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story label (US1, US2, etc.)
- Exact file paths included

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project structure and module initialization

- [X] T001 Add PRESENCE(0x04) packet type to reliable-udp/src/main/kotlin/com/example/reliableudp/protocol/PacketType.kt
- [X] T002 Create udp-cli module directory structure at udp-cli/
- [X] T003 Create udp-cli/build.gradle.kts with kotlinx-cli and reliable-udp dependencies
- [X] T004 Add udp-cli to settings.gradle.kts include list
- [X] T005 [P] Create send package at udp-service/src/main/kotlin/com/example/udpservice/send/
- [X] T006 [P] Create presence package at udp-service/src/main/kotlin/com/example/udpservice/presence/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure for all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 Create DeliveryStatus enum at udp-service/src/main/kotlin/com/example/udpservice/send/DeliveryStatus.kt
- [X] T008 Create OutboundMessageEntity Room entity at udp-service/src/main/kotlin/com/example/udpservice/persistence/OutboundMessageEntity.kt
- [X] T009 Create OutboundMessageDao interface at udp-service/src/main/kotlin/com/example/udpservice/persistence/OutboundMessageDao.kt
- [X] T010 Add outbound_messages table migration (v1→v2) to PacketDatabase at udp-service/src/main/kotlin/com/example/udpservice/persistence/PacketDatabase.kt
- [X] T011 Create SendQueue interface at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueue.kt
- [X] T012 [P] Create RetryScheduler interface at udp-service/src/main/kotlin/com/example/udpservice/send/RetryScheduler.kt
- [X] T013 [P] Create RetryConfig data class at udp-service/src/main/kotlin/com/example/udpservice/send/RetryConfig.kt

**Checkpoint**: Foundation ready - user story implementation can begin

---

## Phase 3: User Story 1 - Send with Guaranteed Persistence (Priority: P1) 🎯 MVP

**Goal**: Messages stored in database before any transmission attempt

**Independent Test**: Send message, force-close app, reopen - message still queued

### Tests for User Story 1

- [X] T014 [P] [US1] Unit test for OutboundMessageEntity at udp-service/src/test/kotlin/com/example/udpservice/db/OutboundMessageEntityTest.kt
- [X] T015 [P] [US1] Unit test for DeliveryStatus state transitions at udp-service/src/test/kotlin/com/example/udpservice/send/DeliveryStatusTest.kt
- [X] T016 [P] [US1] Unit test for SendQueueImpl at udp-service/src/test/kotlin/com/example/udpservice/send/SendQueueImplTest.kt
- [ ] T017 [US1] Integration test for message persistence at udp-service/src/androidTest/kotlin/com/example/udpservice/send/SendQueueIntegrationTest.kt

### Implementation for User Story 1

- [X] T018 [US1] Implement SendQueueImpl with Room persistence at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueueImpl.kt
- [X] T019 [US1] Add send() method to UdpSocket at udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt
- [X] T020 [US1] Wire SendQueue into UdpReceiverService at udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt
- [X] T021 [US1] Expose outboundMessages Flow in UdpReceiver interface at udp-service/src/main/kotlin/com/example/udpservice/UdpReceiver.kt

**Checkpoint**: Messages persist before send, survive app restart

---

## Phase 4: User Story 2 - Automatic Retry with Exponential Backoff (Priority: P2)

**Goal**: Failed deliveries retry with exponential backoff (~63s total)

**Independent Test**: Send to unreachable peer, observe retries at 1s, 2s, 4s, 8s, 16s, 32s intervals

### Tests for User Story 2

- [X] T022 [P] [US2] Unit test for RetrySchedulerImpl at udp-service/src/test/kotlin/com/example/udpservice/send/RetrySchedulerImplTest.kt
- [X] T023 [P] [US2] Unit test for DeliveryTracker at udp-service/src/test/kotlin/com/example/udpservice/send/DeliveryTrackerTest.kt
- [ ] T024 [US2] Integration test for retry behavior at udp-service/src/androidTest/kotlin/com/example/udpservice/send/RetryIntegrationTest.kt

### Implementation for User Story 2

- [X] T025 [US2] Implement RetrySchedulerImpl at udp-service/src/main/kotlin/com/example/udpservice/send/RetrySchedulerImpl.kt
- [X] T026 [US2] Create DeliveryTracker for monitoring delivery results at udp-service/src/main/kotlin/com/example/udpservice/send/DeliveryTracker.kt
- [X] T027 [US2] Implement retry loop in SendQueueImpl using coroutine delay at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueueImpl.kt
- [X] T028 [US2] Handle delivery callbacks from ReliableSocket and update status at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueueImpl.kt
- [X] T029 [US2] Move exhausted messages to WAITING status after timeout at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueueImpl.kt

**Checkpoint**: Messages retry with backoff, move to WAITING after ~63s

---

## Phase 5: User Story 3 - Resume Delivery on Peer Activity (Priority: P3)

**Goal**: WAITING messages resume when any message received from peer

**Independent Test**: Message exhausts retries, receive from peer, delivery resumes

### Tests for User Story 3

- [X] T030 [P] [US3] Unit test for peer activity detection at udp-service/src/test/kotlin/com/example/udpservice/send/PeerActivityTest.kt
- [ ] T031 [US3] Integration test for resume on activity at udp-service/src/androidTest/kotlin/com/example/udpservice/send/ResumeOnActivityTest.kt

### Implementation for User Story 3

- [X] T032 [US3] Add onPeerActivity callback to SendQueue interface at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueue.kt
- [X] T033 [US3] Implement resumeForPeer() in SendQueueImpl at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueueImpl.kt
- [X] T034 [US3] Hook peer activity detection into UdpSocket receive path at udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt
- [X] T035 [US3] Load WAITING messages on service start and check peer activity at udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt

**Checkpoint**: WAITING messages auto-resume when peer shows activity

---

## Phase 6: User Story 4 - Message Delivery Status Visibility (Priority: P4)

**Goal**: Users can see delivery status (delivered, retrying, waiting)

**Independent Test**: Send messages in various states, verify UI shows correct status

### Tests for User Story 4

- [X] T036 [P] [US4] Unit test for OutboundMessage domain model at udp-service/src/test/kotlin/com/example/udpservice/send/OutboundMessageTest.kt
- [ ] T037 [US4] UI test for status display at app/src/androidTest/kotlin/com/example/udpmessaging/OutboundStatusUiTest.kt

### Implementation for User Story 4

- [X] T038 [US4] Create OutboundMessage domain class at udp-service/src/main/kotlin/com/example/udpservice/send/OutboundMessage.kt
- [X] T039 [US4] Add observeAll() and observeForPeer() to SendQueue at udp-service/src/main/kotlin/com/example/udpservice/send/SendQueue.kt
- [X] T040 [US4] Add outbound message list to app UI at app/src/main/kotlin/com/example/udpbroker/ui/OutboundMessageList.kt
- [X] T041 [US4] Display status icons/labels for each delivery state in app UI
- [X] T042 [US4] Add manual retry and cancel actions to UI

**Checkpoint**: Users see real-time delivery status in app

---

## Phase 7: User Story 5 - Presence Broadcast for Peer Discovery (Priority: P5)

**Goal**: Broadcast presence on service start to trigger peer delivery

**Independent Test**: Start service, broadcast sent, peers with pending messages resume

### Tests for User Story 5

- [X] T043 [P] [US5] Unit test for PresenceBroadcaster at udp-service/src/test/kotlin/com/example/udpservice/presence/PresenceBroadcasterTest.kt
- [X] T044 [P] [US5] Unit test for presence packet handling in ReliableSocket at reliable-udp/src/test/kotlin/com/example/reliableudp/PresencePacketTest.kt
- [ ] T045 [US5] Device test for broadcast over real network at udp-service/src/androidTest/kotlin/com/example/udpservice/presence/PresenceBroadcastDeviceTest.kt (tag @DeviceOnly)

### Implementation for User Story 5

- [X] T046 [US5] Create PresenceConfig data class at udp-service/src/main/kotlin/com/example/udpservice/presence/PresenceConfig.kt
- [X] T047 [US5] Implement PresenceBroadcaster at udp-service/src/main/kotlin/com/example/udpservice/presence/PresenceBroadcasterImpl.kt
- [X] T048 [US5] Handle PRESENCE packet type in ReliableSocketImpl at reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocketImpl.kt
- [X] T049 [US5] Add presence callback to UdpSocket at udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt
- [X] T050 [US5] Send presence broadcast on foreground service start at udp-service/src/main/kotlin/com/example/udpservice/UdpReceiverService.kt
- [X] T051 [US5] Add rate limiting (30s min interval) to PresenceBroadcaster

**Checkpoint**: Presence broadcast triggers delivery resume on peers

---

## Phase 8: User Story 6 - Kotlin CLI Tool (Priority: P6)

**Goal**: Replace Python tool with Kotlin CLI wrapping reliable-udp

**Independent Test**: Use CLI to send message to app, message received

### Tests for User Story 6

- [X] T052 [P] [US6] Unit test for UdpClient at udp-cli/src/test/kotlin/com/example/udpcli/UdpClientTest.kt
- [X] T053 [P] [US6] Unit test for SendCommand at udp-cli/src/test/kotlin/com/example/udpcli/commands/SendCommandTest.kt
- [X] T054 [P] [US6] Unit test for ReceiveCommand at udp-cli/src/test/kotlin/com/example/udpcli/commands/ReceiveCommandTest.kt

### Implementation for User Story 6

- [X] T055 [US6] Create Main.kt with kotlinx-cli setup at udp-cli/src/main/kotlin/com/example/udpcli/Main.kt
- [X] T056 [US6] Implement UdpClient wrapping ReliableSocketImpl at udp-cli/src/main/kotlin/com/example/udpcli/UdpClient.kt
- [X] T057 [US6] Implement SendCommand at udp-cli/src/main/kotlin/com/example/udpcli/commands/SendCommand.kt
- [X] T058 [US6] Implement ReceiveCommand at udp-cli/src/main/kotlin/com/example/udpcli/commands/ReceiveCommand.kt
- [X] T059 [US6] Implement BroadcastCommand at udp-cli/src/main/kotlin/com/example/udpcli/commands/BroadcastCommand.kt
- [X] T060 [US6] Add installDist task for CLI distribution in build.gradle.kts
- [X] T061 [US6] Remove Python udp-sender from tools/ directory
- [X] T062 [US6] Update CLAUDE.md with new CLI commands

**Checkpoint**: Kotlin CLI fully replaces Python tool

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, cleanup, final validation

- [X] T063 [P] Update README.md with send functionality documentation
- [ ] T064 [P] Add KDoc to all public APIs in send/ and presence/ packages
- [ ] T065 Run quickstart.md validation scenarios
- [X] T066 Verify all unit tests pass: ./gradlew test (except CLI test runner issue)
- [ ] T067 Verify all instrumented tests pass: ./gradlew connectedAndroidTest
- [ ] T068 Test full end-to-end flow on physical device

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 - BLOCKS all user stories
- **Phases 3-8 (User Stories)**: All depend on Phase 2 completion
  - Stories can proceed in parallel or sequentially by priority
- **Phase 9 (Polish)**: Depends on desired user stories complete

### User Story Dependencies

- **US1 (P1)**: No dependencies - can start after Phase 2
- **US2 (P2)**: Builds on US1 SendQueue, but independently testable
- **US3 (P3)**: Builds on US1 + US2, but independently testable
- **US4 (P4)**: Builds on US1, independently testable
- **US5 (P5)**: Builds on US1 + US3, independently testable
- **US6 (P6)**: No story dependencies - only needs Phase 1 setup

### Within Each User Story

1. Tests written first (TDD)
2. Tests must FAIL before implementation
3. Models/entities before services
4. Services before integration
5. Story complete before next priority

### Parallel Opportunities

**Phase 2 (Foundational)**:
```
T011 + T012 + T013 can run in parallel (interfaces and config)
```

**User Story 1**:
```
T014 + T015 + T016 can run in parallel (unit tests)
```

**User Story 6 (CLI)** can run entirely in parallel with other stories after Phase 1

---

## Parallel Example: User Story 1

```bash
# Launch all unit tests together:
Task: "Unit test for OutboundMessageEntity"
Task: "Unit test for DeliveryStatus state transitions"
Task: "Unit test for SendQueueImpl"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Send message, force-close, verify persistence
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 → Messages persist → MVP!
3. US2 → Retry with backoff
4. US3 → Auto-resume on activity
5. US4 → Status visibility in UI
6. US5 → Presence broadcast
7. US6 → Kotlin CLI tool
8. Polish → Documentation, final tests

### Parallel Team Strategy

With multiple developers:
1. Team completes Setup + Foundational together
2. Once Foundational done:
   - Developer A: US1 → US2 → US3
   - Developer B: US4 (after US1 done)
   - Developer C: US6 (independent)
   - Developer D: US5 (after US3 done)

---

## Notes

- [P] tasks = different files, no blocking dependencies
- TDD required per constitution - tests fail first
- Each story checkpoint = independently testable
- Commit after each task or logical group
- US6 (CLI) is independent - can run in parallel with all others
