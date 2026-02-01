# Android UDP Service

A local-first Android UDP communication library and standalone message broker.

## The Problem

Building local-first Android apps that receive data while backgrounded currently requires Google's push notification infrastructure. There's no clean way to receive messages from local network (or BLE) sources without cloud dependency.

## The Solution

A foreground service that handles UDP communication in two modes:

1. **Library Mode** - Embed in your app, get your own foreground service for local network communication
2. **Broker Mode** - Standalone shared foreground service for multiple apps (avoids N separate foreground services)

## Core Features

### Real-time Communication (Foreground)
- Send and receive UDP messages with minimal latency via Kotlin Flow
- Works in both library and broker modes

### Background Message Handling
- Messages buffered while consuming app is backgrounded
- Automatic acknowledgment sent to sender when message is stored
- Apps retrieve buffered messages when they wake

### App-Specific Notifications (Opt-in)
- Apps can opt-in to receive notifications when new messages arrive
- "New messages for AppX" notification opens the target app directly
- Silent buffering available for apps that don't want notifications

### Subscription-Based Routing
- Messages contain topic headers for routing
- Apps subscribe to specific topics
- Broker routes messages to appropriate apps based on subscriptions

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    UDP Broker Service                        │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ UDP Socket  │→ │Message Router│→ │ Room Database     │  │
│  │ (receive)   │  │ (by topic)   │  │ (buffered msgs)   │  │
│  └─────────────┘  └──────────────┘  └───────────────────┘  │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ UDP Socket  │← │ Send Queue   │  │ ContentProvider   │  │
│  │ (send)      │  │              │  │ (IPC for apps)    │  │
│  └─────────────┘  └──────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────┘
        ↓ Flow (real-time)              ↑ Query (buffered)
┌───────────────────┐            ┌───────────────────┐
│   Active App      │            │  Backgrounded App │
│ (collecting Flow) │            │  (wakes & pulls)  │
└───────────────────┘            └───────────────────┘
```

## Project Structure

```
android-udp-service/
├── udp-service/          # Library module (AAR)
│   └── src/main/kotlin/
│       ├── UdpBrokerService.kt     # Foreground service
│       ├── UdpSocket.kt            # Coroutine-based socket
│       ├── MessageRouter.kt        # Topic-based routing
│       ├── data/                   # Room database
│       ├── provider/               # ContentProvider for IPC
│       └── api/                    # Public API for consumers
│
├── app/                  # Standalone broker app
│   └── src/main/kotlin/
│       └── MainActivity.kt         # Status UI
│
└── tools/
    └── udp-sender/       # Python test tool (uv-managed)
```

## Message Format

Messages use a simple header format for topic-based routing:

```
[2 bytes: topic length][topic string][payload bytes]
```

Example: A message for topic "weather" with payload "temp=22":
```
\x00\x07weather temp=22
```

## Technical Details

- **Minimum Android:** API 29 (Android 10)
- **Language:** Kotlin with Coroutines
- **Build:** Gradle multi-module (library + app)
- **Storage:** Room for message buffering
- **IPC:** Bound service (real-time) + ContentProvider (buffered)

## Status

Under development. See `docs/plans/` for detailed specifications.
