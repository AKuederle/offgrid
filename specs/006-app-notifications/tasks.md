# Tasks: App Prefix Notifications with Deep Linking

**Input**: Design documents from `/specs/006-app-notifications/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **udp-service module**: `udp-service/src/main/kotlin/com/example/udpservice/`
- **app module**: `app/src/main/kotlin/com/example/udpbroker/`
- **Tests**: `*/src/test/` (unit), `*/src/androidTest/` (instrumented)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add dependencies and create directory structure

- [x] T001 Add WorkManager dependency to `app/build.gradle.kts`
- [x] T002 [P] Create directory structure: `udp-service/src/.../broadcast/`, `udp-service/src/.../registration/`
- [x] T003 [P] Create directory structure: `app/src/.../receiver/`, `app/src/.../worker/`, `app/src/.../notification/`, `app/src/.../ui/navigation/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema changes and core entities that MUST be complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database Schema (Fresh Start)

- [x] T004 Add `isRead: Boolean = false` field to `PacketEntity` in `udp-service/src/.../persistence/PacketEntity.kt`
- [x] T005 Add index `[appId, isRead]` to PacketEntity indices
- [x] T006 Create `AppRegistrationEntity` in `udp-service/src/.../persistence/AppRegistrationEntity.kt` per data-model.md
- [x] T007 Create `AppRegistrationDao` in `udp-service/src/.../persistence/AppRegistrationDao.kt` with CRUD queries
- [x] T008 Add unread queries to `PacketDao` per contracts/packet-dao-extensions.kt: `observeUnreadCount`, `getUnreadCount`, `markAllAsRead`, `markAsRead`
- [x] T009 Modify `PacketDatabase` to add new entity and use `fallbackToDestructiveMigration()` (fresh DB, no migration)

### Registration Repository

- [x] T011 Create `AppRegistration` data class in `udp-service/src/.../registration/AppRegistration.kt` per contract
- [x] T012 Create `RegistrationRepository` interface in `udp-service/src/.../registration/RegistrationRepository.kt` per contract
- [x] T013 Create `RegistrationRepositoryImpl` with Room backing in `udp-service/src/.../registration/RegistrationRepositoryImpl.kt`

**Checkpoint**: Foundation ready - database can store registrations and track read status

---

## Phase 3: User Story 1 - Broadcast on Message Arrival (Priority: P1) 🎯 MVP

**Goal**: When a UDP message arrives for a registered prefix, broker sends explicit broadcast to registered package

**Independent Test**: Send UDP message → verify BroadcastReceiver in app fires with correct prefix

### Implementation for User Story 1

- [x] T014 Create `BroadcastActions` object in `udp-service/src/.../broadcast/BroadcastActions.kt` with `ACTION_NEW_MESSAGE` and `EXTRA_PREFIX` constants
- [x] T015 Create `MessageBroadcaster` interface in `udp-service/src/.../broadcast/MessageBroadcaster.kt` per contract
- [x] T016 Create `MessageBroadcasterImpl` in `udp-service/src/.../broadcast/MessageBroadcasterImpl.kt` using `setPackage()` for explicit broadcast
- [x] T017 Integrate `MessageBroadcaster` into `UdpReceiverService`: after persisting message, look up registration and broadcast
- [x] T018 Unit test for `MessageBroadcasterImpl` verifying intent has correct action, package, and prefix extra

**Checkpoint**: Messages arriving for registered prefixes trigger broadcasts to the registered package

---

## Phase 4: User Story 2 - Background Notifications (Priority: P1) 🎯 MVP

**Goal**: When app is backgrounded and message arrives, show notification with unread count

**Independent Test**: Background app → send UDP message → verify notification appears with correct count

### Implementation for User Story 2

- [x] T019 Create `MessageReceiver` BroadcastReceiver in `app/src/.../receiver/MessageReceiver.kt` that receives `ACTION_NEW_MESSAGE`
- [x] T020 Declare `MessageReceiver` in `app/src/main/AndroidManifest.xml` with intent-filter for `com.example.udpservice.NEW_MESSAGE`
- [x] T021 Create `NotificationChannelManager` in `app/src/.../notification/NotificationChannelManager.kt` for per-prefix channels
- [x] T022 Create `NotificationHelper` interface and impl in `app/src/.../notification/NotificationHelper.kt` per contract
- [x] T023 Create `MessageNotificationWorker` CoroutineWorker in `app/src/.../worker/MessageNotificationWorker.kt` that:
  - Gets unread count from DAO
  - Gets registration for deep link URI
  - Shows/updates notification via NotificationHelper
  - Dismisses notification if unread count is 0
- [x] T024 Wire `MessageReceiver.onReceive()` to enqueue `MessageNotificationWorker` with expedited work
- [ ] T025 [P] Unit test for `NotificationHelper` verifying correct notification properties (optional)
- [ ] T026 [P] Unit test for `MessageNotificationWorker` verifying flow logic (optional)

**Checkpoint**: Backgrounded app receives broadcasts and shows notifications with unread count

---

## Phase 5: User Story 3 - Foreground UI Updates (Priority: P1)

**Goal**: When app is in foreground viewing messages, UI updates immediately without notification

**Independent Test**: Open messages page → send UDP message → verify UI updates, no notification shown

### Implementation for User Story 3

- [x] T027 Create `AppState` object in `app/src/.../AppState.kt` with `activePrefix: String?` property
- [x] T028 Modify `MessageReceiver.onReceive()` to check `AppState.activePrefix` - skip WorkManager if UI is active for this prefix
- [x] T029 Create `MessagesScreen` Composable in `app/src/.../ui/MessagesScreen.kt` that:
  - Observes packets for prefix via Room Flow
  - Sets/clears `AppState.activePrefix` on enter/exit
  - Marks messages as read when viewing
- [x] T030 Integrate `MessagesScreen` with existing navigation/UI structure
- [x] T031 Add `markAllAsRead` call when `MessagesScreen` displays messages

**Checkpoint**: Foreground viewing of messages updates UI immediately and auto-marks as read

---

## Phase 6: User Story 4 - Deep Link Navigation (Priority: P1)

**Goal**: Tapping notification opens app to the correct message page via deep link

**Independent Test**: Tap notification → verify app navigates to correct prefix's message page

### Implementation for User Story 4

- [x] T032 Create `AppNavigation.kt` in `app/src/.../ui/navigation/AppNavigation.kt` with route definitions and deep link patterns
- [x] T033 Add `NavHost` with `composable` routes including `navDeepLink { uriPattern = "udptest://messages/{prefix}" }`
- [x] T034 Declare deep link intent-filter in `AndroidManifest.xml` for `udptest://` scheme
- [x] T035 Modify `MainActivity` to handle deep link intents and pass to NavController
- [x] T036 Ensure `NotificationHelper` creates PendingIntent with deep link URI from registration

**Checkpoint**: Notification taps navigate directly to correct message page

---

## Phase 7: User Story 5 - Dual Prefix Test Configuration (Priority: P2)

**Goal**: Broker app registers "broker" and "alerts" prefixes, displays on separate tabs/pages

**Independent Test**: Send messages to each prefix → verify they appear on separate pages, separate notifications

### Implementation for User Story 5

- [x] T037 Create initialization code in app startup to register "broker" and "alerts" prefixes:
  - `prefix: "broker", packageName: context.packageName, deepLinkUri: "udptest://messages/broker"`
  - `prefix: "alerts", packageName: context.packageName, deepLinkUri: "udptest://messages/alerts"`
- [x] T038 Create `BrokerApp` Composable in `app/src/.../ui/BrokerApp.kt` with tab navigation for "broker" and "alerts"
- [x] T039 Integrate `MessagesScreen` for each prefix in tab navigation
- [x] T040 Update `MainActivity` to use `BrokerApp` as root composable
- [x] T041 Verify separate notification channels created for each prefix

**Checkpoint**: App shows two tabs/pages with messages separated by prefix, separate notifications per prefix

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final integration testing and cleanup

- [x] T042 Run `./gradlew :udp-service:test :app:test` to verify all unit tests pass
- [x] T043 Manual test: background app, send messages to both prefixes, verify separate notifications
- [x] T044 Manual test: tap notification, verify deep link navigation works (tested via adb intent)
- [x] T045 Manual test: foreground on one prefix page, send message to that prefix, verify immediate UI update (Note: test app shows notifications always for easier testing)
- [x] T046 Automated test: verify unread counts are accurate (`./gradlew :app:connectedAndroidTest`)
- [x] T047 Service auto-starts on app open (simplified user experience)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-7)**: All depend on Foundational phase completion
  - US1 (broadcast) is prerequisite for US2-US5 (they need broadcasts to test)
  - US2 (background notifications) can proceed after US1
  - US3 (foreground updates) can proceed after US1, parallel with US2
  - US4 (deep links) depends on US2 (notifications must exist to tap)
  - US5 (dual prefix) depends on US1-US4 being functional
- **Polish (Phase 8)**: Depends on all user stories being complete

### Within Each User Story

- Models/entities before services
- Services before integration
- Integration before tests (for manual verification)

### Parallel Opportunities

- T002 and T003 (directory creation) can run in parallel
- T025 and T026 (unit tests) can run in parallel after their dependencies
- Once Foundational phase completes, US2 and US3 can potentially run in parallel (different concerns)

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1 (Broadcast)
4. Complete Phase 4: US2 (Background Notifications)
5. **STOP and VALIDATE**: Background notifications should work
6. Continue with US3, US4, US5

### Incremental Delivery

Each user story adds testable value:
- After US1: Broadcasts fire when messages arrive
- After US2: Background notifications appear
- After US3: Foreground UI updates immediately
- After US4: Notifications deep link correctly
- After US5: Full dual-prefix test configuration works

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story
- Each user story should be independently testable after completion
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
