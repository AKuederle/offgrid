# reliable-udp

A Kotlin library providing reliable message delivery over UDP with automatic retransmission, fragmentation, and delivery confirmation.

## Features

- **Selective Repeat ARQ with SACK** - Efficient acknowledgment of received packets
- **Automatic Fragmentation** - Messages up to 64KB split into MTU-safe packets
- **RTT Estimation** - RFC 9002 EWMA algorithm for adaptive retransmission
- **Delivery Callbacks** - Know when messages are delivered or failed
- **Pure Kotlin** - No native dependencies, Android API 29+ compatible

## Quick Start

```kotlin
// Create and bind socket
val socket = ReliableSocketImpl()
socket.bind(5000)

// Send a message
val destination = InetSocketAddress("192.168.1.100", 5000)
socket.sendAsync(destination, "Hello".toByteArray()) { result ->
    when (result) {
        is DeliveryResult.Success -> println("Delivered!")
        is DeliveryResult.Failure -> println("Failed: ${result.reason}")
    }
}

// Receive messages
socket.messages.collect { message ->
    println("From ${message.source}: ${String(message.payload)}")
}
```

## Wire Protocol

11-byte header (big-endian):

```
┌──────────┬──────────┬───────────┬───────────┬──────────────┐
│ Type (1) │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
└──────────┴──────────┴───────────┴───────────┴──────────────┘

Packet Types:
  0x01 = DATA  - Payload packet (requires ACK)
  0x02 = ACK   - Selective acknowledgment
  0x03 = PING  - Keep-alive / RTT probe
```

## Limitations

- **Maximum message size**: 64KB (255 fragments × ~1400 bytes)
- **Maximum payload per packet**: 1400 bytes (MTU-safe default)
- **No encryption**: Security should be handled at application layer

---

# Future: Stream Support for Large Data

## Problem

The current 64KB message limit is insufficient for:
- File transfers (images, documents)
- Video streaming
- Large data synchronization

## Proposed Solution: STREAM_DATA Packet Type

Add a new packet type `0x04 = STREAM_DATA` that enables chunked delivery of arbitrarily large data with progressive delivery semantics.

### Design Goals

1. **Backward compatible** - Existing header format unchanged
2. **Progressive delivery** - App receives data as chunks arrive
3. **Bounded memory** - Only buffer one chunk at a time
4. **Partial success** - Earlier chunks usable even if later ones fail

### Wire Format

```
Existing header (11 bytes):
┌──────────┬──────────┬───────────┬───────────┬──────────────┐
│Type=0x04 │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
└──────────┴──────────┴───────────┴───────────┴──────────────┘

Stream payload header (12 bytes):
┌────────────┬─────────────┬─────────────┬──────────────────┐
│ StreamID(4)│ ChunkIdx(4) │ ChunkTotal(4)│ Chunk data...   │
└────────────┴─────────────┴─────────────┴──────────────────┘
```

- **StreamID**: Unique identifier for this stream (allows multiplexing)
- **ChunkIdx**: 0-based index of this chunk (4 bytes = 4 billion chunks)
- **ChunkTotal**: Total chunks in stream (0 = unknown/streaming)
- **Chunk data**: Up to 64KB per chunk (one reliable message)

### Proposed API

```kotlin
// Sender
val stream = socket.openStream(destination)
stream.write(largeByteArray)  // Auto-chunks into STREAM_DATA messages
stream.close()

// Sender with progress
socket.sendStream(destination, inputStream) { progress ->
    println("${progress.bytesSent} / ${progress.totalBytes}")
}

// Receiver
socket.streams.collect { stream ->
    // Progressive reading
    stream.chunks.collect { chunk ->
        processChunk(chunk)
    }

    // Or read all at once
    val allData = stream.readAll()
}
```

### Comparison: Message vs Stream

| Aspect | Message (current) | Stream (proposed) |
|--------|-------------------|-------------------|
| Max size | 64KB | Unlimited |
| Delivery | All-or-nothing | Progressive |
| Memory | O(message size) | O(chunk size) |
| Failure | Lose entire message | Lose one chunk |
| Use case | Small payloads | Files, video |

### Implementation Plan

1. **Phase 1**: Add `STREAM_DATA` packet type to protocol
2. **Phase 2**: Implement `StreamSender` with chunking logic
3. **Phase 3**: Implement `StreamReceiver` with reassembly
4. **Phase 4**: Add flow control (optional backpressure)
5. **Phase 5**: Python tool support

### Open Questions

- Should ChunkTotal=0 mean "unknown length" for true streaming?
- Flow control: Should receiver be able to pause sender?
- Should streams support seek/random access?
- Error handling: Retry individual chunks or abort stream?

---

## Configuration

The payload size is configurable for networks with different MTU:

```kotlin
// Default: 1400 bytes (safe for most networks)
val socket = ReliableSocketImpl()

// Custom: For known high-MTU networks
val socket = ReliableSocketImpl(
    fragmentSender = FragmentSenderImpl(maxPayloadSize = 1472)
)
```

**Common MTU values**:
- Ethernet: 1500 → payload 1472
- WiFi: 1500 → payload 1472
- VPN/Tunnels: 1400-1450 → payload 1350-1400
- Mobile (some): 1400 → payload 1350

## License

Apache 2.0

## Attribution

This implementation borrows patterns from:
- [Quincy QUIC](https://github.com/protocol7/quincy) - PacketBuffer, AckQueue (Apache 2.0)
- [RFC 9002](https://www.rfc-editor.org/rfc/rfc9002.html) - RTT estimation, loss detection
