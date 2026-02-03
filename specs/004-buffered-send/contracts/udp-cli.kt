/**
 * UDP CLI Contract
 *
 * Feature: 004-buffered-send
 * Purpose: Defines the interface for the Kotlin CLI tool
 */
package com.example.udpcli

import kotlinx.coroutines.flow.Flow
import java.net.InetSocketAddress

/**
 * Result of a CLI send operation.
 */
sealed class CliSendResult {
    /** Message delivered successfully */
    data class Delivered(val messageId: Int) : CliSendResult()

    /** Message delivery failed */
    data class Failed(val messageId: Int, val reason: String) : CliSendResult()

    /** Operation timed out */
    data class Timeout(val messageId: Int) : CliSendResult()
}

/**
 * Received message from CLI.
 */
data class CliReceivedMessage(
    val source: InetSocketAddress,
    val payload: ByteArray,
    val receivedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CliReceivedMessage) return false
        return source == other.source && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return 31 * source.hashCode() + payload.contentHashCode()
    }
}

/**
 * Interface for the UDP client used by CLI.
 *
 * Wraps the reliable-udp library for command-line use.
 */
interface UdpClient {
    /**
     * Send a message to a peer.
     *
     * Blocks until delivery is confirmed or times out.
     *
     * @param host Target host
     * @param port Target port
     * @param message Message content
     * @param timeoutMs Maximum time to wait for delivery (default 10 seconds)
     * @return CliSendResult indicating outcome
     */
    suspend fun send(
        host: String,
        port: Int,
        message: ByteArray,
        timeoutMs: Long = 10_000
    ): CliSendResult

    /**
     * Start receiving messages.
     *
     * @param port Port to listen on
     * @return Flow of received messages
     */
    fun receive(port: Int): Flow<CliReceivedMessage>

    /**
     * Send a presence broadcast.
     *
     * @param port Port to broadcast on
     * @return true if broadcast sent, false if failed
     */
    suspend fun broadcastPresence(port: Int): Boolean

    /**
     * Close the client and release resources.
     */
    fun close()
}

/**
 * CLI command definitions.
 *
 * Commands:
 *   send     -h HOST -p PORT [-a APPID] -m MESSAGE
 *   receive  -p PORT [-a APPID]
 *   broadcast -p PORT
 *
 * Examples:
 *   udp-cli send -h 192.168.1.100 -p 5000 -m "Hello"
 *   udp-cli send -h 192.168.1.100 -p 5000 -a broker -m "Hello broker"
 *   udp-cli receive -p 5000
 *   udp-cli receive -p 5000 -a broker
 *   udp-cli broadcast -p 5000
 */

// Command-line argument specifications (for kotlinx-cli)

/**
 * Send command arguments.
 */
data class SendArgs(
    /** Target host (required) */
    val host: String,

    /** Target port (default 5000) */
    val port: Int = 5000,

    /** Optional app ID prefix */
    val appId: String? = null,

    /** Message to send (required) */
    val message: String,

    /** Timeout in milliseconds (default 10000) */
    val timeout: Long = 10_000
)

/**
 * Receive command arguments.
 */
data class ReceiveArgs(
    /** Port to listen on (default 5000) */
    val port: Int = 5000,

    /** Optional app ID filter */
    val appId: String? = null
)

/**
 * Broadcast command arguments.
 */
data class BroadcastArgs(
    /** Port to broadcast on (default 5000) */
    val port: Int = 5000
)
