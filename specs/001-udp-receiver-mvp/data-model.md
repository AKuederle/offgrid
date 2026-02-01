# Data Model: UDP Receiver MVP

**Date**: 2026-02-01
**Feature**: 001-udp-receiver-mvp

## Entities

### UdpPacket

Represents a single UDP datagram received by the service.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `data` | `ByteArray` | Raw packet payload | Non-null, max 65535 bytes |
| `sourceAddress` | `InetSocketAddress` | Sender IP and port | Non-null |
| `timestamp` | `Long` | Reception time (epoch millis) | Non-null, positive |
| `displayText` | `String` | Human-readable representation | Derived from data |

**Validation Rules**:
- `data` must not exceed UDP maximum datagram size (65535 bytes)
- `sourceAddress` must be a valid IP:port combination
- `timestamp` must be positive and represent a valid time

**Display Logic**:
- If `data` is valid UTF-8: show as string (truncated to 200 chars)
- Otherwise: show hex preview (first 32 bytes)

---

### ServiceState

Represents the current state of the UDP receiver service.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `status` | `Status` | Running/Stopped/Error | Enum value |
| `port` | `Int?` | Bound UDP port | Null if stopped, 1-65535 if running |
| `localAddresses` | `List<String>` | Device IP addresses | Empty if stopped |
| `packetCount` | `Int` | Total packets received | Non-negative |
| `errorMessage` | `String?` | Error details if status is Error | Null unless Error |

**Status Enum**:
```
STOPPED   -> Initial state, no socket bound
STARTING  -> Transitional, socket being created
RUNNING   -> Socket bound, receiving packets
ERROR     -> Failed to start or runtime error
```

**State Transitions**:
```
STOPPED --[start()]--> STARTING --[success]--> RUNNING
                                 --[failure]--> ERROR
RUNNING --[stop()]--> STOPPED
RUNNING --[error]--> ERROR
ERROR --[start()]--> STARTING
```

---

### PacketLog (UI-only)

In-memory list of received packets for UI display. Not persisted.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| `entries` | `List<UdpPacket>` | Recent packets | Max 100 entries, newest first |

**Behavior**:
- New packets prepended to list
- When list exceeds 100, oldest entries dropped
- Cleared when service stopped

---

## Relationships

```
┌─────────────────┐         ┌──────────────┐
│ UdpReceiverService │──────▶│ ServiceState │
└─────────────────┘         └──────────────┘
        │
        │ emits
        ▼
┌─────────────────┐         ┌──────────────┐
│   UdpPacket     │◀────────│  PacketLog   │
└─────────────────┘ stored  └──────────────┘
                            (UI layer only)
```

- Service owns a single `ServiceState` (1:1)
- Service emits `UdpPacket` instances via Flow (1:many)
- UI maintains `PacketLog` from collected packets (separate from service)

---

## Python Tool Data

### UdpMessage (Python)

Simple container for outgoing UDP message.

| Field | Type | Description |
|-------|------|-------------|
| `payload` | `bytes` | Message content |
| `host` | `str` | Target IP address |
| `port` | `int` | Target port (default 5000) |

No validation beyond standard socket constraints.
