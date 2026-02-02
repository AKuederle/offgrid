# Implementation Plan: Reliable Transport Layer

**Branch**: `003-reliable-transport` | **Date**: 2026-02-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-reliable-transport/spec.md`

## Summary

Implement a custom reliable transport layer on top of UDP using Selective Repeat ARQ with SACK, borrowing proven concepts from QUIC RFC 9002. The implementation will be a standalone Gradle module (`:reliable-udp`) that provides automatic retry, fragmentation/reassembly for large messages, and delivery confirmation while maintaining backward compatibility with the existing `UdpReceiver` interface.

## Technical Context

**Language/Version**: Kotlin 1.9.x with Coroutines 1.7.3
**Primary Dependencies**: kotlinx-coroutines-core (existing), no new dependencies
**Storage**: N/A (in-memory packet buffers only)
**Testing**: JUnit 5 + MockK (Stage 1), AndroidX Test (Stage 2/3)
**Target Platform**: Android API 29+ (no native libraries)
**Project Type**: Mobile (Android library module)
**Performance Goals**: <50ms latency overhead on LAN, 100% delivery at 30% loss
**Constraints**: <10% memory increase, pure Kotlin (no JNI/Netty), offline-capable
**Scale/Scope**: Max 64KB messages, 100 pending fragments buffer

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Test-First Development | ✅ PASS | TDD required - tests before implementation |
| II. Library-First Architecture | ✅ PASS | New `:reliable-udp` module, interfaces for DI |
| III. Local-First Design | ✅ PASS | No cloud dependencies, pure local UDP |
| IV. Simplicity and YAGNI | ✅ PASS | Only QUIC concepts needed for reliability |
| V. Clean Abstractions | ✅ PASS | Program against interfaces, DI for all deps |

**Post-Design Re-check**: All principles satisfied. New module follows library-first architecture.

## Project Structure

### Documentation (this feature)

```text
specs/003-reliable-transport/
├── plan.md              # This file
├── research.md          # Phase 0: Quincy/QUIC research findings
├── data-model.md        # Phase 1: Entity definitions
├── quickstart.md        # Phase 1: Getting started guide
├── contracts/           # Phase 1: Internal API contracts
└── tasks.md             # Phase 2: Implementation tasks
```

### Source Code (repository root)

```text
reliable-udp/                              # NEW: Standalone library module
├── src/main/kotlin/com/example/reliableudp/
│   ├── ReliableSocket.kt                  # Main entry point, wraps DatagramSocket
│   ├── ReliablePacket.kt                  # Header + payload with serialization
│   ├── protocol/
│   │   ├── PacketType.kt                  # DATA=0x01, ACK=0x02, PING=0x03
│   │   ├── Header.kt                      # 12-byte header parsing/writing
│   │   └── AckFrame.kt                    # SACK range encoding/decoding
│   ├── sender/
│   │   ├── PacketBuffer.kt                # Tracks sent-but-unacked packets
│   │   ├── FragmentSender.kt              # Splits large messages into fragments
│   │   └── RetransmitTimer.kt             # PTO-based retransmission
│   ├── receiver/
│   │   ├── AckQueue.kt                    # Batches received packets for SACK
│   │   ├── FragmentBuffer.kt              # Reassembles out-of-order fragments
│   │   └── DeduplicationCache.kt          # Prevents duplicate delivery
│   └── rtt/
│       └── RttEstimator.kt                # Smoothed RTT, variance, PTO calculation
├── src/test/kotlin/                       # Stage 1: JVM unit tests
│   └── com/example/reliableudp/
│       ├── protocol/HeaderTest.kt
│       ├── protocol/AckFrameTest.kt
│       ├── sender/PacketBufferTest.kt
│       ├── sender/FragmentSenderTest.kt
│       ├── receiver/AckQueueTest.kt
│       ├── receiver/FragmentBufferTest.kt
│       └── rtt/RttEstimatorTest.kt
└── build.gradle.kts

udp-service/                               # EXISTING: Uses reliable-udp as dependency
├── src/main/kotlin/com/example/udpservice/
│   ├── UdpReceiver.kt                     # Interface (unchanged)
│   ├── UdpSocket.kt                       # Updated to use ReliableSocket
│   └── ...
└── build.gradle.kts                       # Add: implementation(project(":reliable-udp"))

tools/udp-sender/                          # EXISTING: Python test tool
├── src/udp_sender/
│   ├── reliable.py                        # NEW: Reliable protocol implementation
│   └── ...
└── tests/
    └── test_reliable.py                   # NEW: Protocol tests
```

**Structure Decision**: New `:reliable-udp` module follows Library-First Architecture principle. The module is self-contained with no Android framework dependencies (pure Kotlin), enabling independent testing and potential reuse outside this project.

## Complexity Tracking

No constitution violations. The design follows all principles:
- New module justified by FR-011 (standalone Gradle module requirement)
- Interface-based design enables DI and testability
- No premature abstractions - only QUIC concepts specified in requirements
