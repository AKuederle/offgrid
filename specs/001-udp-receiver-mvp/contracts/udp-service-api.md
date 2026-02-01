# UDP Service API Contract

**Date**: 2026-02-01
**Feature**: 001-udp-receiver-mvp

## Overview

This document defines the internal API contract for the `udp-service` library module. Since this is an Android library (not a REST API), contracts are defined as Kotlin interfaces.

## UdpReceiver Interface

The primary interface for receiving UDP packets.

```kotlin
/**
 * Interface for receiving UDP datagrams.
 * Implementations handle socket lifecycle and emit packets via Flow.
 */
interface UdpReceiver {
    /**
     * Flow of received packets. Collectors receive packets in real-time.
     * Flow is cold - collection starts receiving only while collector is active.
     */
    val packets: SharedFlow<UdpPacket>

    /**
     * Current state of the receiver.
     */
    val state: StateFlow<ReceiverState>

    /**
     * Start listening for UDP packets on the specified port.
     * @param port UDP port to bind (1-65535)
     * @throws IllegalStateException if already running
     * @throws IOException if port binding fails
     */
    suspend fun start(port: Int = 5000)

    /**
     * Stop listening and release the socket.
     * Safe to call when already stopped.
     */
    fun stop()
}
```

## ReceiverState

```kotlin
sealed interface ReceiverState {
    data object Stopped : ReceiverState
    data object Starting : ReceiverState
    data class Running(val port: Int, val addresses: List<String>) : ReceiverState
    data class Error(val message: String) : ReceiverState
}
```

## UdpPacket

```kotlin
/**
 * Immutable representation of a received UDP datagram.
 */
data class UdpPacket(
    val data: ByteArray,
    val sourceAddress: InetSocketAddress,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Human-readable display text.
     * UTF-8 string if valid, hex preview otherwise.
     */
    val displayText: String
        get() = tryDecodeUtf8() ?: toHexPreview()

    private fun tryDecodeUtf8(): String? = // ...
    private fun toHexPreview(): String = // ...

    // equals/hashCode must consider ByteArray content
}
```

## Service Binder Contract

```kotlin
/**
 * Binder interface for binding to UdpReceiverService.
 */
interface UdpServiceBinder {
    /**
     * Get the receiver instance for packet observation.
     */
    fun getReceiver(): UdpReceiver
}
```

## Usage Pattern

```kotlin
// Bind to service
val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val receiver = (binder as UdpServiceBinder).getReceiver()

        // Observe packets
        scope.launch {
            receiver.packets.collect { packet ->
                // Handle packet
            }
        }

        // Observe state
        scope.launch {
            receiver.state.collect { state ->
                // Update UI
            }
        }
    }
}
bindService(intent, connection, Context.BIND_AUTO_CREATE)
```

## Thread Safety

- All interface methods are safe to call from any thread
- `start()` is a suspend function and should be called from a coroutine
- `stop()` can be called synchronously
- Flows emit on `Dispatchers.IO`; collectors should switch to Main for UI updates
