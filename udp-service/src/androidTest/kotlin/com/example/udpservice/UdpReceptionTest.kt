package com.example.udpservice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.udpservice.api.ReceiverState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Stage 2 Emulator Tests: UDP packet reception via localhost.
 *
 * These tests verify core UDP functionality using 127.0.0.1 loopback,
 * which works identically on emulator and physical device.
 */
@RunWith(AndroidJUnit4::class)
class UdpReceptionTest {

    private lateinit var udpSocket: UdpSocket
    private val testPort = 15000 // Use high port to avoid conflicts

    @Before
    fun setup() {
        udpSocket = UdpSocket()
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

        // Send a test packet
        val testMessage = "Hello from test"
        sendUdpPacket("127.0.0.1", testPort, testMessage)

        // Wait for packet to be received
        withTimeout(5000) {
            val packet = udpSocket.packets.first()
            assertEquals(testMessage, packet.displayText)
            assertEquals(InetAddress.getLoopbackAddress(), packet.sourceAddress.address)
        }
    }

    @Test
    fun receivesMultiplePacketsInOrder() = runBlocking {
        udpSocket.start(testPort)

        withTimeout(5000) {
            udpSocket.state.first { it is ReceiverState.Running }
        }

        // Send multiple packets
        val messages = listOf("packet-1", "packet-2", "packet-3")
        messages.forEach { msg ->
            sendUdpPacket("127.0.0.1", testPort, msg)
            Thread.sleep(50) // Small delay to ensure ordering
        }

        // Collect exactly 3 packets
        val receivedMessages = withTimeout(5000) {
            udpSocket.packets.take(3).toList().map { it.displayText }
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
        sendUdpPacket("127.0.0.1", testPort, unicodeMessage)

        withTimeout(5000) {
            val packet = udpSocket.packets.first()
            assertEquals(unicodeMessage, packet.displayText)
        }
    }

    private fun sendUdpPacket(host: String, port: Int, message: String) {
        DatagramSocket().use { socket ->
            val data = message.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
        }
    }
}
