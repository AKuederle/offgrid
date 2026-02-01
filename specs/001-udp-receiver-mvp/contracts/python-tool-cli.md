# Python UDP Sender CLI Contract

**Date**: 2026-02-01
**Feature**: 001-udp-receiver-mvp

## Overview

Command-line interface specification for the `udp-sender` Python tool.

## Commands

### `udp-sender send`

Send a single UDP packet.

```
Usage: udp-sender send [OPTIONS]

Options:
  -h, --host TEXT     Target host IP address [required]
  -p, --port INTEGER  Target port [default: 5000]
  -m, --message TEXT  Message to send [required]
  --help              Show this message and exit.
```

**Examples**:
```bash
# Send simple message
uv run udp-sender send -h 192.168.1.100 -m "hello"

# Send to custom port
uv run udp-sender send -h 192.168.1.100 -p 8080 -m "custom port"
```

**Exit Codes**:
- 0: Success
- 1: Network error (unreachable, etc.)
- 2: Invalid arguments

---

### `udp-sender flood`

Send packets at a specified rate for load testing.

```
Usage: udp-sender flood [OPTIONS]

Options:
  -h, --host TEXT       Target host IP address [required]
  -p, --port INTEGER    Target port [default: 5000]
  -r, --rate INTEGER    Packets per second [default: 100]
  -d, --duration INTEGER  Duration in seconds [default: 10]
  --help                Show this message and exit.
```

**Examples**:
```bash
# Default: 100 packets/sec for 10 seconds
uv run udp-sender flood -h 192.168.1.100

# High rate for 5 seconds
uv run udp-sender flood -h 192.168.1.100 -r 500 -d 5
```

**Output**:
```
Flooding 192.168.1.100:5000 at 100 msg/s for 10s...
Sent 1000 packets
```

**Exit Codes**:
- 0: Success
- 1: Network error
- 2: Invalid arguments

---

### `udp-sender interactive`

Interactive mode for manual packet sending.

```
Usage: udp-sender interactive [OPTIONS]

Options:
  -h, --host TEXT     Target host IP address [required]
  -p, --port INTEGER  Target port [default: 5000]
  --help              Show this message and exit.
```

**Examples**:
```bash
uv run udp-sender interactive -h 192.168.1.100
```

**Session**:
```
Interactive mode - sending to 192.168.1.100:5000
Type message and press Enter to send. Ctrl+C to exit.

> hello world
Sent: hello world (11 bytes)
> test message
Sent: test message (12 bytes)
> ^C
Goodbye!
```

**Exit Codes**:
- 0: Normal exit (Ctrl+C or EOF)
- 1: Network error
- 2: Invalid arguments

---

## Global Options

```
Usage: udp-sender [OPTIONS] COMMAND [ARGS]...

UDP test sender for Android UDP Service.

Options:
  --version  Show version and exit.
  --help     Show this message and exit.

Commands:
  send         Send a single UDP packet
  flood        Send packets at a specified rate
  interactive  Interactive mode for manual sending
```

## Wire Format

Packets are sent as raw bytes. For text messages:
- Encoding: UTF-8
- No framing or headers (raw payload)
- Maximum size: 65535 bytes (UDP limit)

## Installation

No installation required. Run via uv:

```bash
cd tools/udp-sender
uv run udp-sender --help
```

Dependencies managed via `pyproject.toml`.
