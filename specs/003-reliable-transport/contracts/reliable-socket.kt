/**
 * API Contract: ReliableSocket
 *
 * Feature: 003-reliable-transport
 * Module: :reliable-udp
 *
 * This file defines the public API contract for the reliable transport layer.
 * Implementation must satisfy these interfaces exactly.
 */

package com.example.reliableudp

import kotlinx.coroutines.flow.Flow
import java.net.InetSocketAddress

/**
 * Main entry point for reliable UDP communication.
 *
 * Wraps a DatagramSocket with automatic retry, fragmentation, and delivery confirmation.
 * Thread-safe and coroutine-friendly.
 *
 * Usage:
 * ```kotlin
 * val socket = ReliableSocket()
 * socket.bind(5000)
 *
 * // Receive messages
 * socket.messages.collect { message ->
 *     println("Received: ${message.payload.decodeToString()}")
 * }
 *
 * // Send with confirmation
 * val result = socket.send(destination, payload)
 * when (result) {
 *     is DeliveryResult.Success -> println("Delivered!")
 *     is DeliveryResult.Failure -> println("Failed: ${result.reason}")
 * }
 * ```
 */
interface ReliableSocket {
    /**
     * Flow of complete, reassembled messages.
     *
     * Messages are emitted only after all fragments are received and reassembled.
     * Duplicates are filtered out. Order is not guaranteed.
     */
    val messages: Flow<ReceivedMessage>

    /**
     * Current socket state.
     */
    val state: Flow<SocketState>

    /**
     * Bind to a local port and start receiving.
     *
     * @param port The UDP port to listen on
     * @throws IllegalStateException if already bound
     * @throws java.net.BindException if port is in use
     */
    suspend fun bind(port: Int)

    /**
     * Send a message reliably to the destination.
     *
     * Large messages are automatically fragmented. Retransmission is automatic
     * until acknowledgment or max retries exceeded.
     *
     * @param destination Target address and port
     * @param payload Message data (max 64KB)
     * @return Delivery result indicating success or failure reason
     * @throws IllegalStateException if not bound
     * @throws IllegalArgumentException if payload exceeds MAX_MESSAGE_SIZE
     */
    suspend fun send(destination: InetSocketAddress, payload: ByteArray): DeliveryResult

    /**
     * Send a message and receive delivery confirmation via callback.
     *
     * Non-blocking version that invokes callback when delivery completes.
     *
     * @param destination Target address and port
     * @param payload Message data (max 64KB)
     * @param onResult Callback invoked with delivery result
     * @return Message ID for tracking
     */
    fun sendAsync(
        destination: InetSocketAddress,
        payload: ByteArray,
        onResult: (DeliveryResult) -> Unit
    ): Int

    /**
     * Close the socket and release resources.
     *
     * Pending sends will fail with SOCKET_CLOSED reason.
     */
    fun close()
}

/**
 * A complete message received from a remote sender.
 */
data class ReceivedMessage(
    val payload: ByteArray,
    val source: InetSocketAddress,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceivedMessage) return false
        return payload.contentEquals(other.payload) &&
                source == other.source &&
                receivedAt == other.receivedAt
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + receivedAt.hashCode()
        return result
    }
}

/**
 * Socket lifecycle states.
 */
sealed class SocketState {
    object Unbound : SocketState()
    object Binding : SocketState()
    data class Bound(val port: Int) : SocketState()
    data class Error(val message: String) : SocketState()
    object Closed : SocketState()
}

/**
 * Result of a send operation.
 */
sealed class DeliveryResult {
    /**
     * Message was acknowledged by the receiver.
     */
    data class Success(
        val messageId: Int,
        val deliveredAt: Long = System.currentTimeMillis()
    ) : DeliveryResult()

    /**
     * Message delivery failed.
     */
    data class Failure(
        val messageId: Int,
        val reason: FailureReason,
        val retryCount: Int
    ) : DeliveryResult()

    enum class FailureReason {
        /** All retry attempts exhausted without acknowledgment */
        MAX_RETRIES_EXCEEDED,
        /** Overall send timeout exceeded */
        TIMEOUT,
        /** Socket was closed during send */
        SOCKET_CLOSED
    }
}
