package com.example.reliableudp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress

class ReceivedMessageTest {

    @Test
    fun `equals compares payload content`() {
        val payload = "Hello".toByteArray()
        val source = InetSocketAddress("127.0.0.1", 5000)
        val timestamp = 12345L

        val msg1 = ReceivedMessage(payload.copyOf(), source, timestamp)
        val msg2 = ReceivedMessage(payload.copyOf(), source, timestamp)

        assertEquals(msg1, msg2)
    }

    @Test
    fun `equals returns false for different payload`() {
        val source = InetSocketAddress("127.0.0.1", 5000)
        val timestamp = 12345L

        val msg1 = ReceivedMessage("Hello".toByteArray(), source, timestamp)
        val msg2 = ReceivedMessage("World".toByteArray(), source, timestamp)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `equals returns false for different source`() {
        val payload = "Hello".toByteArray()
        val timestamp = 12345L

        val msg1 = ReceivedMessage(payload, InetSocketAddress("127.0.0.1", 5000), timestamp)
        val msg2 = ReceivedMessage(payload, InetSocketAddress("127.0.0.1", 5001), timestamp)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `equals returns false for different timestamp`() {
        val payload = "Hello".toByteArray()
        val source = InetSocketAddress("127.0.0.1", 5000)

        val msg1 = ReceivedMessage(payload, source, 12345L)
        val msg2 = ReceivedMessage(payload, source, 67890L)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val payload = "Hello".toByteArray()
        val source = InetSocketAddress("127.0.0.1", 5000)
        val timestamp = 12345L

        val msg1 = ReceivedMessage(payload.copyOf(), source, timestamp)
        val msg2 = ReceivedMessage(payload.copyOf(), source, timestamp)

        assertEquals(msg1.hashCode(), msg2.hashCode())
    }

    @Test
    fun `default timestamp uses current time`() {
        val before = System.currentTimeMillis()
        val msg = ReceivedMessage("Hello".toByteArray(), InetSocketAddress("127.0.0.1", 5000))
        val after = System.currentTimeMillis()

        assertTrue(msg.receivedAt >= before)
        assertTrue(msg.receivedAt <= after)
    }

    @Test
    fun `toString includes relevant info`() {
        val msg = ReceivedMessage(
            "Hello".toByteArray(),
            InetSocketAddress("127.0.0.1", 5000),
            12345L
        )

        val str = msg.toString()
        assertTrue(str.contains("5 bytes"))
        assertTrue(str.contains("127.0.0.1"))
        assertTrue(str.contains("5000"))
    }
}

class SocketStateTest {

    @Test
    fun `Unbound toString`() {
        assertEquals("Unbound", SocketState.Unbound.toString())
    }

    @Test
    fun `Binding toString`() {
        assertEquals("Binding", SocketState.Binding.toString())
    }

    @Test
    fun `Bound toString includes port`() {
        val state = SocketState.Bound(5000)
        assertTrue(state.toString().contains("5000"))
    }

    @Test
    fun `Error toString includes message`() {
        val state = SocketState.Error("Test error")
        assertTrue(state.toString().contains("Test error"))
    }

    @Test
    fun `Closed toString`() {
        assertEquals("Closed", SocketState.Closed.toString())
    }
}

class DeliveryResultTest {

    @Test
    fun `Success has default timestamp`() {
        val before = System.currentTimeMillis()
        val result = DeliveryResult.Success(1)
        val after = System.currentTimeMillis()

        assertTrue(result.deliveredAt >= before)
        assertTrue(result.deliveredAt <= after)
    }

    @Test
    fun `Success with custom timestamp`() {
        val result = DeliveryResult.Success(1, 12345L)
        assertEquals(12345L, result.deliveredAt)
    }

    @Test
    fun `Failure has all fields`() {
        val result = DeliveryResult.Failure(
            messageId = 42,
            reason = DeliveryResult.FailureReason.MAX_RETRIES_EXCEEDED,
            retryCount = 5
        )

        assertEquals(42, result.messageId)
        assertEquals(DeliveryResult.FailureReason.MAX_RETRIES_EXCEEDED, result.reason)
        assertEquals(5, result.retryCount)
    }

    @Test
    fun `FailureReason values exist`() {
        val reasons = DeliveryResult.FailureReason.entries
        assertTrue(reasons.contains(DeliveryResult.FailureReason.MAX_RETRIES_EXCEEDED))
        assertTrue(reasons.contains(DeliveryResult.FailureReason.TIMEOUT))
        assertTrue(reasons.contains(DeliveryResult.FailureReason.SOCKET_CLOSED))
    }
}
