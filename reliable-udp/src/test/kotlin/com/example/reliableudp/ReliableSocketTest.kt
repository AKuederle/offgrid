package com.example.reliableudp

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress

class ReliableSocketTest {

    private var socket: ReliableSocketImpl? = null

    @AfterEach
    fun cleanup() {
        socket?.close()
        socket = null
    }

    @Test
    fun `initial state is Unbound`() = runTest {
        socket = ReliableSocketImpl()
        val state = socket!!.state.first()
        assertEquals(SocketState.Unbound, state)
    }

    @Test
    fun `bind transitions to Bound state`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0) // Bind to any available port

        val state = socket!!.state.first()
        assertTrue(state is SocketState.Bound)
        assertTrue((state as SocketState.Bound).port > 0)
    }

    @Test
    fun `bind twice throws IllegalStateException`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        assertThrows<IllegalStateException> {
            runTest { socket!!.bind(0) }
        }
    }

    @Test
    fun `send before bind throws IllegalStateException`() = runTest {
        socket = ReliableSocketImpl()
        val destination = InetSocketAddress("127.0.0.1", 5000)

        assertThrows<IllegalStateException> {
            runTest { socket!!.send(destination, "Hello".toByteArray()) }
        }
    }

    @Test
    fun `sendAsync before bind throws IllegalStateException`() {
        socket = ReliableSocketImpl()
        val destination = InetSocketAddress("127.0.0.1", 5000)

        assertThrows<IllegalStateException> {
            socket!!.sendAsync(destination, "Hello".toByteArray()) { }
        }
    }

    @Test
    fun `send rejects oversized payload`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val oversized = ByteArray(ReliableUdpConstants.MAX_MESSAGE_SIZE + 1)
        val destination = InetSocketAddress("127.0.0.1", 5000)

        val exception = assertThrows<IllegalArgumentException> {
            socket!!.send(destination, oversized)
        }
        assertTrue(exception.message?.contains("exceeds maximum") == true)
    }

    @Test
    fun `sendAsync rejects oversized payload`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val oversized = ByteArray(ReliableUdpConstants.MAX_MESSAGE_SIZE + 1)
        val destination = InetSocketAddress("127.0.0.1", 5000)

        assertThrows<IllegalArgumentException> {
            socket!!.sendAsync(destination, oversized) { }
        }
    }

    @Test
    fun `close transitions to Closed state`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)
        socket!!.close()

        val state = socket!!.state.first()
        assertEquals(SocketState.Closed, state)
    }

    @Test
    fun `sendAsync returns unique message IDs`() = runTest {
        socket = ReliableSocketImpl()
        socket!!.bind(0)

        val destination = InetSocketAddress("127.0.0.1", 5000)
        val ids = mutableSetOf<Int>()

        repeat(10) {
            val id = socket!!.sendAsync(destination, "Test".toByteArray()) { }
            ids.add(id)
        }

        assertEquals(10, ids.size) // All unique
    }

    @Test
    fun `messages flow is available`() {
        socket = ReliableSocketImpl()
        assertNotNull(socket!!.messages)
    }

    @Test
    fun `state flow is available`() {
        socket = ReliableSocketImpl()
        assertNotNull(socket!!.state)
    }
}
