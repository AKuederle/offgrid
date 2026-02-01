<!--
Sync Impact Report
==================
Version change: 1.0.1 → 1.1.0 (MINOR: Add 3-stage testing model)

Modified sections:
- Testing on Android: Expanded from 2-stage to 3-stage testing model
  - Stage 1: Unit tests (JVM)
  - Stage 2: Emulator tests (CI-compatible instrumented)
  - Stage 3: Physical device tests (device-only, tagged @DeviceOnly)

Templates requiring updates:
- .specify/templates/plan-template.md: ✅ Compatible
- .specify/templates/spec-template.md: ✅ Compatible
- .specify/templates/tasks-template.md: ✅ Compatible

Follow-up TODOs:
- Create @DeviceOnly annotation in test infrastructure
- Update CI pipeline to run Stage 1 + Stage 2
-->

# Android UDP Service Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)

All production code MUST be written using Test-Driven Development:

- Tests MUST be written before implementation code
- Tests MUST fail before implementation begins (Red phase)
- Implementation MUST be minimal to pass tests (Green phase)
- Refactoring MUST maintain passing tests (Refactor phase)
- Unit tests MUST cover as much logic as possible
- Integration and instrumented tests MUST run on actual Android devices
- No shortcuts or workarounds that compromise test coverage

**Rationale**: Tests are the specification. Writing tests first ensures clear requirements and prevents regression. Device testing catches platform-specific issues that emulators miss.

### II. Library-First Architecture

Every feature MUST start as a standalone library module:

- Library modules MUST be self-contained and independently testable
- Public APIs MUST be defined through interfaces (program against interfaces, not implementations)
- Dependencies MUST be injected, not instantiated internally (Dependency Injection/IoC)
- Libraries MUST have clear, documented purpose
- No organizational-only modules without functional purpose

**Rationale**: Library-first design enables reuse, simplifies testing, and maintains clear boundaries. DI enables testability and flexibility.

### III. Local-First Design

All functionality MUST work without cloud dependencies:

- UDP communication MUST work entirely on local network
- No Google Play Services or push notification dependencies for core features
- Offline capability MUST be preserved
- Data MUST be stored locally (Room database)
- External network calls are prohibited for core functionality

**Rationale**: This project exists specifically to avoid cloud dependency for local network communication on Android.

### IV. Simplicity and YAGNI

MUST start with the simplest viable solution:

- No premature abstractions or over-engineering
- Build only what is needed now, not hypothetical future needs
- If workarounds accumulate, STOP and consult the user
- Clean, long-term maintainability over quick fixes
- Complexity MUST be explicitly justified against simpler alternatives

**Rationale**: Simplicity reduces bugs, improves maintainability, and keeps the codebase accessible.

### V. Clean Abstractions

Code MUST be structured for testability and maintainability:

- Program against interfaces, not concrete implementations
- Use Dependency Injection/Inversion of Control for all dependencies
- Avoid static singletons; prefer injected dependencies
- Keep classes focused (Single Responsibility Principle)
- Favor composition over inheritance

**Rationale**: Clean abstractions enable unit testing with mocks, support future refactoring, and maintain separation of concerns.

## Quality Standards

### Code Review Requirements

- All changes MUST go through Pull Request review
- PRs MUST include relevant tests
- PRs MUST pass all existing tests before merge
- Code style MUST follow project linting/formatting rules

### Testing Coverage

- Business logic MUST have unit test coverage
- Public APIs MUST have contract tests
- Critical user flows MUST have integration tests
- Instrumented tests MUST validate device-specific behavior
- Test failures MUST block merges

### Documentation Standards

- Public APIs MUST have KDoc documentation
- Complex algorithms MUST have explanatory comments
- README MUST stay current with project capabilities
- Breaking changes MUST be documented

## Android-Specific Constraints

### Platform Requirements

- **Minimum SDK**: API 29 (Android 10)
- **Language**: Kotlin with Coroutines
- **Build System**: Gradle multi-module
- **Architecture**: MVVM or clean architecture patterns
- **Async**: Kotlin Coroutines and Flow (no RxJava)

### Android Best Practices

- Foreground Service MUST comply with Android notification requirements
- Background work MUST respect Doze mode and battery optimization
- Room MUST be used for structured local storage
- ContentProvider MUST be used for secure IPC
- Lifecycle-aware components MUST be used where applicable

### Testing on Android

**Test Organization**: All tests MUST be grouped into three distinct stages:

1. **Stage 1: Unit Tests** (`test/` source set)
   - Pure Kotlin logic tests that run on JVM
   - JUnit 5 + MockK
   - No Android framework dependencies
   - Fast execution (<1 second per test), no real I/O or sleeps
   - Run command: `./gradlew test`

2. **Stage 2: Emulator Tests** (`androidTest/` source set, CI-compatible)
   - Instrumented tests that run on Android emulator
   - Service lifecycle, UI state, localhost networking
   - Tests MUST work on standard CI emulator (no special hardware)
   - Use `127.0.0.1` for network loopback tests
   - Run command: `./gradlew connectedAndroidTest`

3. **Stage 3: Physical Device Tests** (`androidTest/` source set, device-only)
   - Tests requiring real hardware or network conditions
   - Real WiFi IP detection, boot receiver, battery behavior
   - Tagged with `@DeviceOnly` annotation for filtering
   - Run manually or in dedicated device lab
   - Run command: `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.udpservice.test.DeviceOnly`

**Test Placement Rules**:
- Tests MUST be placed in the correct source set based on dependencies
- Emulator tests MUST NOT depend on real network IPs or device-specific hardware
- Physical device tests MUST be tagged `@DeviceOnly` for CI exclusion
- Avoid Robolectric; prefer pure unit tests or real instrumented tests
- Do NOT test trivial code (getters, data classes, simple wiring)

**CI Pipeline**:
- Stage 1 (Unit): Runs on every commit, blocks merge on failure
- Stage 2 (Emulator): Runs on every commit, blocks merge on failure
- Stage 3 (Device): Runs manually or on release branches only

## Development Workflow

### Branch Strategy

- Feature branches MUST be created for all changes
- Branch names MUST follow pattern: `feature/description` or `fix/description`
- PRs MUST be created immediately when creating feature branches using `gh pr create`
- Main branch MUST remain stable and deployable

### PR Process

1. Create feature branch
2. Create draft PR immediately using `gh pr create --draft`
3. Implement with TDD (tests first)
4. Mark PR ready for review when complete
5. Address review feedback
6. Merge after approval

### Commit Guidelines

- Commits MUST have clear, descriptive messages
- Use conventional commit format: `type: description`
- Types: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`
- Keep commits atomic and focused

## Governance

This constitution documents the development principles for Android UDP Service. Changes follow a lightweight process:

- Constitution changes MUST be documented with rationale
- Version MUST be incremented according to semantic versioning:
  - MAJOR: Principle removals or backward-incompatible redefinitions
  - MINOR: New principles or sections added
  - PATCH: Clarifications, wording improvements
- All dependent templates MUST be checked for consistency after amendments

The constitution serves as guidance rather than rigid law. When principles conflict with practical reality, document the deviation and its justification. If workarounds become frequent, revisit the relevant principle.

**Version**: 1.1.0 | **Ratified**: 2026-02-01 | **Last Amended**: 2026-02-01
