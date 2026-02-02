# Tasks: Reliable Transport Layer

**Input**: Design documents from `/specs/003-reliable-transport/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included per constitution (Test-First Development is NON-NEGOTIABLE)

**Organization**: Tasks grouped by user story for independent implementation and testing

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- All paths relative to repository root

## User Stories Summary

| Story | Title | Priority | Key Components |
|-------|-------|----------|----------------|
| US1 | Reliable Delivery Under Loss | P1 | PacketBuffer, RetransmitTimer, RttEstimator, AckQueue |
| US2 | Large Message Fragmentation | P1 | FragmentSender, FragmentBuffer |
| US3 | Backward-Compatible API | P1 | UdpSocket integration |
| US4 | Sender Delivery Confirmation | P2 | DeliveryResult callbacks |

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Create the `:reliable-udp` module structure and configure dependencies

- [x] T001 Create reliable-udp module directory structure per plan.md in reliable-udp/
- [x] T002 Create build.gradle.kts with Kotlin JVM plugin and coroutines dependency in reliable-udp/build.gradle.kts
- [x] T003 Add `:reliable-udp` module to settings.gradle.kts
- [x] T004 [P] Create package directories for protocol/, sender/, receiver/, rtt/ in reliable-udp/src/main/kotlin/com/example/reliableudp/
- [x] T005 [P] Create test package directories in reliable-udp/src/test/kotlin/com/example/reliableudp/

---

## Phase 2: Foundational (Protocol Layer)

**Purpose**: Wire protocol definitions that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests for Foundational Components

- [x] T006 [P] Unit test for PacketType enum and byte conversion in reliable-udp/src/test/kotlin/com/example/reliableudp/protocol/PacketTypeTest.kt
- [x] T007 [P] Unit test for Header serialization/deserialization in reliable-udp/src/test/kotlin/com/example/reliableudp/protocol/HeaderTest.kt
- [x] T008 [P] Unit test for AckFrame and AckRange encoding/decoding in reliable-udp/src/test/kotlin/com/example/reliableudp/protocol/AckFrameTest.kt
- [x] T009 [P] Unit test for Constants validation in reliable-udp/src/test/kotlin/com/example/reliableudp/ReliableUdpConstantsTest.kt

### Implementation for Foundational Components

- [x] T010 [P] Create ReliableUdpConstants object with protocol constants in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableUdpConstants.kt
- [x] T011 [P] Create PacketType enum (DATA=0x01, ACK=0x02, PING=0x03) in reliable-udp/src/main/kotlin/com/example/reliableudp/protocol/PacketType.kt
- [x] T012 [P] Create Header data class with 11-byte serialization in reliable-udp/src/main/kotlin/com/example/reliableudp/protocol/Header.kt
- [x] T013 Create AckFrame and AckRange data classes with SACK encoding in reliable-udp/src/main/kotlin/com/example/reliableudp/protocol/AckFrame.kt (depends on T012)

**Checkpoint**: Protocol layer ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Reliable Delivery Under Loss (Priority: P1) 🎯 MVP

**Goal**: Implement Selective Repeat ARQ with SACK for automatic retry on packet loss

**Independent Test**: Send 50 messages over simulated 30% packet loss, verify all received intact

**Key Requirements**: FR-001, FR-004, FR-005, FR-006

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T014 [P] [US1] Unit test for RttEstimator EWMA and PTO calculation in reliable-udp/src/test/kotlin/com/example/reliableudp/rtt/RttEstimatorTest.kt
- [x] T015 [P] [US1] Unit test for PacketBuffer put/remove/getOlderThan operations in reliable-udp/src/test/kotlin/com/example/reliableudp/sender/PacketBufferTest.kt
- [x] T016 [P] [US1] Unit test for AckQueue enqueue/drain with range coalescing in reliable-udp/src/test/kotlin/com/example/reliableudp/receiver/AckQueueTest.kt
- [x] T017 [P] [US1] Unit test for DeduplicationCache checkAndMark and compact in reliable-udp/src/test/kotlin/com/example/reliableudp/receiver/DeduplicationCacheTest.kt
- [x] T018 [P] [US1] Unit test for RetransmitTimer PTO-based retransmission triggers in reliable-udp/src/test/kotlin/com/example/reliableudp/sender/RetransmitTimerTest.kt

### Implementation for User Story 1

- [x] T019 [P] [US1] Create RttEstimator interface in reliable-udp/src/main/kotlin/com/example/reliableudp/rtt/RttEstimator.kt
- [x] T020 [US1] Implement RttEstimatorImpl with RFC 9002 EWMA algorithm in reliable-udp/src/main/kotlin/com/example/reliableudp/rtt/RttEstimatorImpl.kt (depends on T019)
- [x] T021 [P] [US1] Create PacketBuffer interface in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/PacketBuffer.kt
- [x] T022 [US1] Implement PacketBufferImpl with ConcurrentHashMap in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/PacketBufferImpl.kt (depends on T021)
- [x] T023 [P] [US1] Create SentPacket data class in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/SentPacket.kt
- [x] T024 [P] [US1] Create AckQueue interface in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/AckQueue.kt
- [x] T025 [US1] Implement AckQueueImpl with Channel and range coalescing in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/AckQueueImpl.kt (depends on T024)
- [x] T026 [P] [US1] Create DeduplicationCache interface in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/DeduplicationCache.kt
- [x] T027 [US1] Implement DeduplicationCacheImpl with LRU eviction in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/DeduplicationCacheImpl.kt (depends on T026)
- [x] T028 [P] [US1] Create RetransmitTimer interface in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/RetransmitTimer.kt
- [x] T029 [US1] Implement RetransmitTimerImpl with coroutine-based loop in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/RetransmitTimerImpl.kt (depends on T028, T020, T022)

**Checkpoint**: Core reliability components ready (ARQ, SACK, RTT, retransmit)

---

## Phase 4: User Story 2 - Large Message Fragmentation (Priority: P1)

**Goal**: Automatic fragmentation/reassembly for messages >1400 bytes (up to 64KB)

**Independent Test**: Send 64KB message, verify received intact with correct reassembly

**Key Requirements**: FR-002, FR-003

### Tests for User Story 2

- [x] T030 [P] [US2] Unit test for FragmentSender splitting messages at 1400 bytes in reliable-udp/src/test/kotlin/com/example/reliableudp/sender/FragmentSenderTest.kt
- [x] T031 [P] [US2] Unit test for FragmentBuffer reassembly with out-of-order fragments in reliable-udp/src/test/kotlin/com/example/reliableudp/receiver/FragmentBufferTest.kt
- [x] T032 [P] [US2] Unit test for FragmentBuffer stale message cleanup (30s timeout) in reliable-udp/src/test/kotlin/com/example/reliableudp/receiver/FragmentBufferTest.kt

### Implementation for User Story 2

- [x] T033 [P] [US2] Create FragmentSender interface in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/FragmentSender.kt
- [x] T034 [US2] Implement FragmentSenderImpl with MTU-based splitting in reliable-udp/src/main/kotlin/com/example/reliableudp/sender/FragmentSenderImpl.kt (depends on T033, T023)
- [x] T035 [P] [US2] Create FragmentBuffer interface in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/FragmentBuffer.kt
- [x] T036 [US2] Implement FragmentBufferImpl with ConcurrentHashMap and timeout cleanup in reliable-udp/src/main/kotlin/com/example/reliableudp/receiver/FragmentBufferImpl.kt (depends on T035)

**Checkpoint**: Fragmentation layer ready - can send/receive messages up to 64KB

---

## Phase 5: User Story 3 - Backward-Compatible API (Priority: P1)

**Goal**: Maintain existing UdpReceiver interface contract while adding reliability

**Independent Test**: Existing unit tests pass without modification

**Key Requirements**: FR-007, FR-008

### Tests for User Story 3

- [x] T037 [P] [US3] Unit test for ReliableSocket interface contract in reliable-udp/src/test/kotlin/com/example/reliableudp/ReliableSocketTest.kt
- [x] T038 [P] [US3] Unit test for ReceivedMessage and SocketState in reliable-udp/src/test/kotlin/com/example/reliableudp/ReceivedMessageTest.kt
- [x] T039 [US3] Integration test for ReliableSocket send/receive loop in reliable-udp/src/test/kotlin/com/example/reliableudp/ReliableSocketIntegrationTest.kt (depends on all US1, US2 components)

### Implementation for User Story 3

- [x] T040 [P] [US3] Create ReliableSocket interface in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocket.kt
- [x] T041 [P] [US3] Create ReceivedMessage data class in reliable-udp/src/main/kotlin/com/example/reliableudp/ReceivedMessage.kt
- [x] T042 [P] [US3] Create SocketState sealed class in reliable-udp/src/main/kotlin/com/example/reliableudp/SocketState.kt
- [x] T043 [P] [US3] Create DeliveryResult sealed class in reliable-udp/src/main/kotlin/com/example/reliableudp/DeliveryResult.kt
- [x] T044 [US3] Implement ReliableSocketImpl with DI for all components in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocketImpl.kt (depends on T040-T043, all US1/US2 components)
- [x] T045 [US3] Add reliable-udp dependency to udp-service/build.gradle.kts
- [x] T046 [US3] Update UdpSocket to use ReliableSocket in udp-service/src/main/kotlin/com/example/udpservice/UdpSocket.kt (depends on T044, T045)
- [x] T047 [US3] Verify existing UdpSocket tests still pass in udp-service/src/test/kotlin/com/example/udpservice/UdpSocketTest.kt

**Checkpoint**: Existing API preserved - can use reliable transport transparently

---

## Phase 6: User Story 4 - Sender Delivery Confirmation (Priority: P2)

**Goal**: Provide delivery confirmation callback to senders

**Independent Test**: Sender callback invoked within 500ms of receiver ACK on local network

**Key Requirements**: FR-009

### Tests for User Story 4

- [x] T048 [P] [US4] Unit test for delivery success callback timing in reliable-udp/src/test/kotlin/com/example/reliableudp/DeliveryCallbackTest.kt
- [x] T049 [P] [US4] Unit test for delivery failure callback with reason in reliable-udp/src/test/kotlin/com/example/reliableudp/DeliveryCallbackTest.kt

### Implementation for User Story 4

- [x] T050 [US4] Add message completion tracking to ReliableSocketImpl in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocketImpl.kt (depends on T044)
- [x] T051 [US4] Implement sendAsync with callback invocation in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocketImpl.kt (depends on T050)
- [x] T052 [US4] Wire up RetransmitTimer onMaxRetriesExceeded to failure callback in reliable-udp/src/main/kotlin/com/example/reliableudp/ReliableSocketImpl.kt (depends on T050, T029)

**Checkpoint**: Delivery confirmation working - senders know when messages arrive

---

## Phase 7: Python Sender Tool (Priority: P1)

**Goal**: Update Python UDP sender tool with reliable protocol support

**Key Requirements**: FR-010

### Tests for Python Tool

- [x] T053 [P] Unit test for reliable protocol header encoding/decoding in tools/udp-sender/tests/test_reliable.py
- [x] T054 [P] Unit test for ACK frame parsing in tools/udp-sender/tests/test_reliable.py
- [x] T055 Unit test for message fragmentation in Python in tools/udp-sender/tests/test_reliable.py (depends on T053)

### Implementation for Python Tool

- [x] T056 [P] Create reliable.py module with ReliablePacket class in tools/udp-sender/src/udp_sender/reliable.py
- [x] T057 Implement header serialization matching Kotlin format in tools/udp-sender/src/udp_sender/reliable.py (depends on T056)
- [x] T058 Implement ACK frame parsing in Python in tools/udp-sender/src/udp_sender/reliable.py (depends on T056)
- [x] T059 Add --reliable flag to CLI with retransmit logic in tools/udp-sender/src/udp_sender/cli.py (depends on T057, T058)

**Checkpoint**: Python tool can send/receive using reliable protocol

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, code attribution, and final validation

- [x] T060 [P] Add Apache 2.0 attribution comments to files borrowing from Quincy (PacketBuffer, AckQueue patterns) per FR-012
- [x] T061 [P] Add RFC 9002 reference comments to RttEstimator and loss detection logic per FR-012
- [x] T062 [P] Update README.md with reliable transport usage examples in reliable-udp/README.md
- [x] T063 [P] Update CLAUDE.md with new module context
- [ ] T064 Run quickstart.md validation scenarios - DEFERRED: requires end-to-end testing environment
- [ ] T065 Performance validation: verify <50ms latency overhead per NFR-002 - DEFERRED: requires benchmarking
- [ ] T066 Memory validation: verify <10% increase per NFR-001 - DEFERRED: requires profiling

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    │
    ▼
Phase 2 (Foundational - Protocol Layer)
    │
    ├──────────────────┬──────────────────┬──────────────────┐
    ▼                  ▼                  ▼                  │
Phase 3 (US1)      Phase 4 (US2)     Phase 7 (Python)      │
Reliability        Fragmentation      Tool                  │
    │                  │                  │                  │
    └──────────────────┴──────────────────┘                  │
                       │                                     │
                       ▼                                     │
                Phase 5 (US3)                               │
                API Integration                              │
                       │                                     │
                       ▼                                     │
                Phase 6 (US4)                               │
                Delivery Callbacks                           │
                       │                                     │
                       ▼                                     │
                Phase 8 (Polish) ◄───────────────────────────┘
```

### User Story Dependencies

- **US1 (Reliability)**: Depends only on Foundational (Phase 2)
- **US2 (Fragmentation)**: Depends only on Foundational (Phase 2) - parallel with US1
- **US3 (API)**: Depends on US1 + US2 (needs all reliability components)
- **US4 (Callbacks)**: Depends on US3 (needs ReliableSocketImpl)
- **Python Tool**: Depends only on Foundational (Phase 2) - parallel with US1/US2

### Parallel Opportunities

#### Within Phase 2 (Foundational)
```
T006, T007, T008, T009 can run in parallel (all tests)
T010, T011, T012 can run in parallel (independent files)
T013 depends on T012 (needs Header)
```

#### Within Phase 3 (US1)
```
T014, T015, T016, T017, T018 can run in parallel (all tests)
T019, T021, T023, T024, T026, T028 can run in parallel (interfaces)
T020, T022, T025, T027 depend on their interfaces
T029 depends on T020, T022 (needs RttEstimator, PacketBuffer)
```

#### Within Phase 4 (US2)
```
T030, T031, T032 can run in parallel (all tests)
T033, T035 can run in parallel (interfaces)
T034 depends on T033, T023
T036 depends on T035
```

#### Across Phases (after Phase 2)
```
US1 (Phase 3) and US2 (Phase 4) can run in parallel
Python Tool (Phase 7) can run in parallel with US1/US2
```

---

## Parallel Example: Phase 3 (User Story 1)

```bash
# Launch all tests for US1 together:
Task: "T014 [P] [US1] Unit test for RttEstimator..."
Task: "T015 [P] [US1] Unit test for PacketBuffer..."
Task: "T016 [P] [US1] Unit test for AckQueue..."
Task: "T017 [P] [US1] Unit test for DeduplicationCache..."
Task: "T018 [P] [US1] Unit test for RetransmitTimer..."

# Launch all interfaces for US1 together:
Task: "T019 [P] [US1] Create RttEstimator interface..."
Task: "T021 [P] [US1] Create PacketBuffer interface..."
Task: "T023 [P] [US1] Create SentPacket data class..."
Task: "T024 [P] [US1] Create AckQueue interface..."
Task: "T026 [P] [US1] Create DeduplicationCache interface..."
Task: "T028 [P] [US1] Create RetransmitTimer interface..."
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2 + 3)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (Protocol Layer)
3. Complete Phase 3: User Story 1 (Core Reliability)
4. Complete Phase 4: User Story 2 (Fragmentation)
5. Complete Phase 5: User Story 3 (API Integration)
6. **STOP and VALIDATE**: Test end-to-end reliable delivery
7. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Protocol layer ready
2. Add US1 → Reliability works (single packets)
3. Add US2 → Large messages work (fragmentation)
4. Add US3 → Integrated with existing API
5. Add US4 → Delivery callbacks available
6. Add Python → Full tooling support

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (Reliability)
   - Developer B: US2 (Fragmentation)
   - Developer C: Python Tool
3. After US1 + US2 complete:
   - Developer A: US3 (API Integration)
   - Developer B: US4 (Callbacks)
4. Polish phase: All developers

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Constitution requires TDD: verify tests fail before implementing
- Commit after each task or logical group
- Code attribution required for Quincy/RFC 9002 patterns (FR-012)
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
