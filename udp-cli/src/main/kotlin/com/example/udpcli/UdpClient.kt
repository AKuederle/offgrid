package com.example.udpcli

import com.example.reliableudp.DeliveryResult
import com.example.reliableudp.ReliableSocketImpl
import com.example.reliableudp.SocketState
import com.example.reliableudp.protocol.Header
import com.example.reliableudp.protocol.PacketType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
 */
interface UdpClient {
    suspend fun send(
        host: String,
        port: Int,
        message: ByteArray,
        timeoutMs: Long = 10_000
    ): CliSendResult

    fun receive(port: Int): Flow<CliReceivedMessage>
    suspend fun broadcastPresence(port: Int): Boolean
    fun close()
}

/**
 * Implementation of UdpClient wrapping ReliableSocketImpl.
 */
class UdpClientImpl : UdpClient {

    private var socket: ReliableSocketImpl? = null
    private var boundPort: Int = 0

    override suspend fun send(
        host: String,
        port: Int,
        message: ByteArray,
        timeoutMs: Long
    ): CliSendResult {
        ensureSocketBound(0) // Use ephemeral port for sending

        val destination = InetSocketAddress(host, port)

        return try {
            withTimeout(timeoutMs) {
                val result = socket!!.send(destination, message)
                when (result) {
                    is DeliveryResult.Success -> CliSendResult.Delivered(result.messageId)
                    is DeliveryResult.Failure -> CliSendResult.Failed(
                        result.messageId,
                        result.reason.name
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            CliSendResult.Timeout(-1)
        }
    }

    override fun receive(port: Int): Flow<CliReceivedMessage> {
        // Create socket synchronously for flow
        runBlocking {
            ensureSocketBound(port)
        }

        return socket!!.messages
            .filter { !it.isPresence } // Skip presence packets
            .map { msg ->
                CliReceivedMessage(
                    source = msg.source,
                    payload = msg.payload,
                    receivedAt = msg.receivedAt
                )
            }
    }

    override suspend fun broadcastPresence(port: Int): Boolean {
        return try {
            // Create a simple UDP socket for broadcast
            DatagramSocket().use { broadcastSocket ->
                broadcastSocket.broadcast = true

                // Create presence packet with header
                val header = Header(
                    type = PacketType.PRESENCE,
                    messageId = 0,
                    sequenceNumber = 0,
                    fragmentIndex = 1,
                    fragmentTotal = 1
                )
                val packet = header.toBytes()

                // Broadcast to 255.255.255.255
                val address = InetAddress.getByName("255.255.255.255")
                val datagram = DatagramPacket(packet, packet.size, address, port)
                broadcastSocket.send(datagram)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        socket?.close()
        socket = null
        boundPort = 0
    }

    private suspend fun ensureSocketBound(port: Int) {
        if (socket != null && boundPort == port) return

        // Close existing socket if different port
        socket?.close()

        val newSocket = ReliableSocketImpl()
        newSocket.bind(port)
        socket = newSocket

        // Get the bound port from state flow
        val currentState = newSocket.state.first { it is SocketState.Bound }
        boundPort = (currentState as SocketState.Bound).port
    }
}
