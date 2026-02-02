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
 * val socket = ReliableSocketImpl()
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
     * @throws IllegalStateException if not bound
     * @throws IllegalArgumentException if payload exceeds MAX_MESSAGE_SIZE
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
