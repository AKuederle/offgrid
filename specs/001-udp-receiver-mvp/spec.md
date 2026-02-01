# Feature Specification: UDP Receiver MVP

**Feature Branch**: `001-udp-receiver-mvp`
**Created**: 2026-02-01
**Status**: Draft
**Input**: Android app with foreground service that receives UDP packets, deployable via adb

## Overview

A minimal Android application that runs a foreground service capable of receiving UDP packets over the local network, along with a Python test tool and documentation for setup and testing. This proves the core networking concept works on real hardware before adding complexity like message buffering, routing, or cross-app communication.

## User Scenarios & Testing

### User Story 1 - Start UDP Listener (Priority: P1)

As a developer testing the UDP service, I want to start the service from the app so that it begins listening for incoming UDP packets on the local network.

**Why this priority**: This is the fundamental capability - without a running service, nothing else works.

**Independent Test**: Launch app, tap "Start Service", verify notification appears indicating service is running.

**Acceptance Scenarios**:

1. **Given** the app is installed and opened, **When** I tap "Start Service", **Then** a persistent notification appears showing the service is running with the listening port number.
2. **Given** the service is not running, **When** I tap "Start Service", **Then** the app requests necessary permissions (notifications on Android 13+) before starting.
3. **Given** the service is already running, **When** I open the app, **Then** the UI reflects the running state.

---

### User Story 2 - Receive UDP Packet (Priority: P1)

As a developer testing the UDP service, I want to send a UDP packet from my computer and see it appear in the app so that I can verify the service receives network traffic.

**Why this priority**: This is the core value proposition - proving UDP reception works on a real device.

**Independent Test**: With service running, use the Python UDP sender tool to send a packet, verify it appears in the app's message log.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** I send a UDP packet to the device's IP and listening port, **Then** the packet appears in the app's message log within 1 second.
2. **Given** the service is running, **When** I send multiple UDP packets rapidly, **Then** all packets appear in the message log in order received.
3. **Given** the service is running, **When** I send a packet with arbitrary binary data, **Then** the app displays it (hex or truncated) without crashing.

---

### User Story 3 - Send Test Packets with Python Tool (Priority: P1)

As a developer, I want a simple command-line tool to send UDP packets to the Android device so that I can easily test packet reception without manually using netcat.

**Why this priority**: Essential for practical testing - manual netcat commands are error-prone and tedious.

**Independent Test**: Run `uv run udp-sender send -h <ip> -m "hello"` and verify the tool sends successfully.

**Acceptance Scenarios**:

1. **Given** the tool is installed, **When** I run `udp-sender send -h <ip> -p 5000 -m "hello"`, **Then** the packet is sent to the specified host and port.
2. **Given** the tool is installed, **When** I run `udp-sender flood -h <ip> -r 100 -d 5`, **Then** 100 packets per second are sent for 5 seconds.
3. **Given** the tool is installed, **When** I run `udp-sender interactive -h <ip>`, **Then** I can type messages and send them one at a time.

---

### User Story 4 - View Network Info (Priority: P2)

As a developer testing the UDP service, I want to see the device's IP address and listening port so that I know where to send test packets from my computer.

**Why this priority**: Essential for testing but secondary to actual packet reception.

**Independent Test**: Start service, verify the UI displays a valid local IP address (not 127.0.0.1) and port number that matches where packets should be sent.

**Acceptance Scenarios**:

1. **Given** the service is running and device is on WiFi, **When** I look at the status display, **Then** I see the device's local IP address (e.g., 192.168.x.x) and listening port.
2. **Given** the device has multiple network interfaces, **When** the service starts, **Then** it shows at least the primary WiFi address.

---

### User Story 5 - Stop UDP Listener (Priority: P2)

As a developer, I want to stop the service so that it releases the network port and stops the foreground notification.

**Why this priority**: Important for resource management but not critical for proving the concept.

**Independent Test**: With service running, tap "Stop Service", verify notification disappears and sending packets no longer works.

**Acceptance Scenarios**:

1. **Given** the service is running, **When** I tap "Stop Service", **Then** the notification disappears and the UI shows "Stopped".
2. **Given** the service is stopped, **When** I send a UDP packet to the previous port, **Then** no response occurs (packet is dropped).

---

### User Story 6 - Follow Setup and Testing Guide (Priority: P2)

As a developer or AI agent new to this project, I want clear documentation on how to build, deploy, and test the app so that I can get started without guessing.

**Why this priority**: Enables onboarding and reproducibility; essential for future development.

**Independent Test**: Follow the README instructions from scratch on a clean machine and successfully send/receive a UDP packet.

**Acceptance Scenarios**:

1. **Given** the README exists, **When** I follow the "Prerequisites" section, **Then** I understand what tools I need (Android SDK, uv, physical device).
2. **Given** the README exists, **When** I follow the "Build and Install" section, **Then** I can build the APK and install it on a connected device via adb.
3. **Given** the README exists, **When** I follow the "Testing" section, **Then** I can send a UDP packet and see it appear in the app.
4. **Given** the README exists, **When** I look for the Python tool instructions, **Then** I find how to install and use the UDP sender.

---

### Edge Cases

- What happens when the device loses WiFi connection while service is running?
- How does the app handle packets larger than the receive buffer?
- What happens if the default port (5000) is already in use?
- How does the service behave when the app is killed from recents?

## Requirements

### Functional Requirements

#### Android App

- **FR-001**: App MUST run a foreground service with a persistent notification when listening for UDP packets.
- **FR-002**: Service MUST bind to a UDP port (default 5000) and receive incoming datagrams.
- **FR-003**: Service MUST continue running when app is backgrounded or killed from recents.
- **FR-004**: App MUST display received packet content in a scrollable log (most recent first).
- **FR-005**: App MUST display the device's local IP address and listening port when service is running.
- **FR-006**: App MUST provide controls to start and stop the service.
- **FR-007**: App MUST request POST_NOTIFICATIONS permission on Android 13+ before starting the foreground service.
- **FR-008**: Service MUST use `foregroundServiceType="specialUse"` for Android 14+ compatibility.
- **FR-009**: App MUST be installable via `adb install` on a physical device running Android 10+.

#### Python UDP Sender Tool

- **FR-010**: Tool MUST send a single UDP packet to a specified host and port with a text message.
- **FR-011**: Tool MUST support a "flood" mode that sends packets at a configurable rate for a specified duration.
- **FR-012**: Tool MUST support an "interactive" mode for sending messages one at a time from the terminal.
- **FR-013**: Tool MUST be runnable via `uv run` without requiring a separate install step.

#### Documentation

- **FR-014**: README MUST document prerequisites (Android SDK, uv, physical device, same network).
- **FR-015**: README MUST provide step-by-step instructions for building and installing the Android app.
- **FR-016**: README MUST provide instructions for finding the device IP and testing with the UDP sender tool.
- **FR-017**: README MUST document how to use the Python UDP sender tool (send, flood, interactive modes).

### Key Entities

- **UDP Packet**: Raw bytes received from the network, with source IP, source port, and timestamp.
- **Service State**: Running/Stopped status, bound port, local addresses.

## Success Criteria

### Measurable Outcomes

- **SC-001**: App installs and launches on a physical Android device (API 29+) without crashes.
- **SC-002**: Service starts within 2 seconds of user tap and shows notification.
- **SC-003**: UDP packets sent from the Python tool appear in the app within 1 second.
- **SC-004**: Service continues receiving packets when app is in background for at least 5 minutes.
- **SC-005**: 100 packets sent via flood mode (10ms apart) all appear in the log.
- **SC-006**: A new developer can follow the README and successfully send/receive a packet within 15 minutes.
- **SC-007**: Python tool runs successfully with `uv run udp-sender --help` showing available commands.

## Out of Scope

The following are explicitly NOT part of this MVP:

- Message persistence (Room database)
- Topic-based routing or message parsing
- Cross-app communication (ContentProvider, client API)
- Subscription management
- Automatic acknowledgments
- Sending UDP packets from the Android app (receive only)
- Configuration UI for port selection
- Comprehensive test coverage for edge cases (basic TDD tests included per constitution)

## Assumptions

- Device is connected to a WiFi network accessible from the test computer.
- Default port 5000 is available (no conflict handling in MVP).
- Packets are small enough to fit in a single datagram (no reassembly).
- UTF-8 display of packet content is acceptable; binary shown as hex preview.
- Developer has uv installed for Python tooling.
