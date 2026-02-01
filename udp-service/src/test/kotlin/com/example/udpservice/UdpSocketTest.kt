package com.example.udpservice

import com.example.udpservice.api.ReceiverState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for UdpSocket - tests only state machine logic.
 * Actual socket operations are tested in androidTest (on-device).
 */
@DisplayName("UdpSocket")
class UdpSocketTest {

    @Test
    @DisplayName("initial state is Stopped")
    fun `initial state is Stopped`() {
        val udpSocket = UdpSocket()
        assertEquals(ReceiverState.Stopped, udpSocket.state.value)
    }

    @Test
    @DisplayName("implements UdpReceiver interface")
    fun `implements UdpReceiver interface`() {
        val udpSocket = UdpSocket()
        assertTrue(udpSocket is UdpReceiver)
    }

    @Test
    @DisplayName("packets flow has empty replay cache")
    fun `packets is a SharedFlow`() {
        val udpSocket = UdpSocket()
        assertTrue(udpSocket.packets.replayCache.isEmpty())
    }

    @Test
    @DisplayName("stop on never-started socket is safe")
    fun `stop on never started socket is safe`() {
        val udpSocket = UdpSocket()
        udpSocket.stop()
        assertEquals(ReceiverState.Stopped, udpSocket.state.value)
    }

    @Test
    @DisplayName("stop is idempotent")
    fun `stop is idempotent`() {
        val udpSocket = UdpSocket()
        udpSocket.stop()
        udpSocket.stop()
        udpSocket.stop()
        assertEquals(ReceiverState.Stopped, udpSocket.state.value)
    }
}
