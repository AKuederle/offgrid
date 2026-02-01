package com.example.udpservice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.udpservice.api.ReceiverState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.example.udpservice.api.PacketParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Stage 2: Emulator + Device Tests - UDP packet reception via localhost.
 *
 * These tests verify core UDP functionality using 127.0.0.1 loopback,
 * which works identically on emulator and physical device.
 */
@RunWith(AndroidJUnit4::class)
class UdpReceptionTest {

    private lateinit var udpSocket: UdpSocket
    private val testPort = 15000 // Use high port to avoid conflicts
    private val testAppId = "test"

    @Before
    fun setup() {
        udpSocket = UdpSocket()
        udpSocket.registerAppId(testAppId)
    }

    @After
    fun teardown() {
        udpSocket.stop()
    }

    @Test
    fun socketStartsAndReachesRunningState() = runBlocking {
        udpSocket.start(testPort)

        withTimeout(5000) {
            val state = udpSocket.state.first { it is ReceiverState.Running }
            assertTrue(state is ReceiverState.Running)
            assertEquals(testPort, (state as ReceiverState.Running).port)
        }
    }

    @Test
    fun receivesUdpPacketFromLocalhost() = runBlocking {
        // Start the receiver
        udpSocket.start(testPort)

        withTimeout(5000) {
            udpSocket.state.first { it is ReceiverState.Running }
        }

        val testMessage = "Hello from test"

        // Launch packet collection first, then send
        val packetDeferred = async {
            udpSocket.packets.first()
        }

        // Give collect a moment to start, then send
        delay(200)
        sendUdpPacket("127.0.0.1", testPort, testMessage)

        // Wait for packet
        val packet = withTimeout(10000) {
            packetDeferred.await()
        }

        val parsed = PacketParser.parse(packet.data)
        assertNotNull("Failed to parse packet", parsed)
        assertEquals(testAppId, parsed!!.appId)
        assertEquals(testMessage, String(parsed.payload, Charsets.UTF_8))
        assertTrue(
            "Expected loopback address, got: ${packet.sourceAddress.address}",
            packet.sourceAddress.address.isLoopbackAddress
        )
    }

    @Test
    fun receivesMultiplePacketsInOrder() = runBlocking {
        udpSocket.start(testPort)

        withTimeout(5000) {
            udpSocket.state.first { it is ReceiverState.Running }
        }

        val messages = listOf("packet-1", "packet-2", "packet-3")

        // Start collecting first
        val packetsDeferred = async {
            udpSocket.packets.take(3).toList().map { packet ->
                val parsed = PacketParser.parse(packet.data)
                assertNotNull("Failed to parse packet", parsed)
                String(parsed!!.payload, Charsets.UTF_8)
            }
        }

        // Give collect a moment to start, then send
        delay(200)
        messages.forEach { msg ->
            sendUdpPacket("127.0.0.1", testPort, msg)
            delay(100) // Delay to ensure ordering
        }

        // Wait for all packets
        val receivedMessages = withTimeout(10000) {
            packetsDeferred.await()
        }

        assertEquals(messages, receivedMessages)
    }

    @Test
    fun stopCeasesPacketReception() = runBlocking {
        udpSocket.start(testPort)

        withTimeout(5000) {
            udpSocket.state.first { it is ReceiverState.Running }
        }

        udpSocket.stop()

        withTimeout(5000) {
            val state = udpSocket.state.first { it is ReceiverState.Stopped }
            assertTrue(state is ReceiverState.Stopped)
        }
    }

    @Test
    fun handlesUtf8EncodedPackets() = runBlocking {
        udpSocket.start(testPort)

        withTimeout(5000) {
            udpSocket.state.first { it is ReceiverState.Running }
        }

        val unicodeMessage = "Héllo Wörld 你好 🎉"

        // Start collecting first
        val packetDeferred = async {
            udpSocket.packets.first()
        }

        // Give collect a moment to start, then send
        delay(200)
        sendUdpPacket("127.0.0.1", testPort, unicodeMessage)

        val packet = withTimeout(10000) {
            packetDeferred.await()
        }

        val parsed = PacketParser.parse(packet.data)
        assertNotNull("Failed to parse packet", parsed)
        assertEquals(testAppId, parsed!!.appId)
        assertEquals(unicodeMessage, String(parsed.payload, Charsets.UTF_8))
    }

    private fun sendUdpPacket(host: String, port: Int, message: String) {
        DatagramSocket().use { socket ->
            val data = PacketParser.encode(testAppId, message.toByteArray(Charsets.UTF_8))
                ?: throw IllegalArgumentException("Failed to encode packet")
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
        }
    }
}
