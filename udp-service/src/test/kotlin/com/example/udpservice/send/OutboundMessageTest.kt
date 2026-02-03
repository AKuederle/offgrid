package com.example.udpservice.send

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress

class OutboundMessageTest {

    private val peer = InetSocketAddress("192.168.1.100", 5000)
    private val payload = "test message".toByteArray()
    private val now = System.currentTimeMillis()

    @Test
    fun `isTerminal returns true only for DELIVERED status`() {
        val delivered = createMessage(status = DeliveryStatus.DELIVERED)
        val pending = createMessage(status = DeliveryStatus.PENDING)
        val sending = createMessage(status = DeliveryStatus.SENDING)
        val retrying = createMessage(status = DeliveryStatus.RETRYING)
        val waiting = createMessage(status = DeliveryStatus.WAITING)

        assertTrue(delivered.isTerminal)
        assertFalse(pending.isTerminal)
        assertFalse(sending.isTerminal)
        assertFalse(retrying.isTerminal)
        assertFalse(waiting.isTerminal)
    }

    @Test
    fun `isPending returns true for non-terminal states`() {
        val pending = createMessage(status = DeliveryStatus.PENDING)
        val sending = createMessage(status = DeliveryStatus.SENDING)
        val retrying = createMessage(status = DeliveryStatus.RETRYING)
        val waiting = createMessage(status = DeliveryStatus.WAITING)
        val delivered = createMessage(status = DeliveryStatus.DELIVERED)

        assertTrue(pending.isPending)
        assertTrue(sending.isPending)
        assertTrue(retrying.isPending)
        assertTrue(waiting.isPending)
        assertFalse(delivered.isPending)
    }

    @Test
    fun `equality uses contentEquals for payload`() {
        val msg1 = createMessage()
        val msg2 = createMessage()

        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())
    }

    @Test
    fun `different payloads are not equal`() {
        val msg1 = createMessage(payload = "message1".toByteArray())
        val msg2 = createMessage(payload = "message2".toByteArray())

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `same payload different array instances are equal`() {
        val msg1 = createMessage(payload = "same".toByteArray())
        val msg2 = createMessage(payload = "same".toByteArray())

        assertEquals(msg1, msg2)
    }

    @Test
    fun `different ids are not equal`() {
        val msg1 = createMessage(id = 1)
        val msg2 = createMessage(id = 2)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `different peers are not equal`() {
        val msg1 = createMessage(peer = InetSocketAddress("192.168.1.100", 5000))
        val msg2 = createMessage(peer = InetSocketAddress("192.168.1.101", 5000))

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `different status are not equal`() {
        val msg1 = createMessage(status = DeliveryStatus.PENDING)
        val msg2 = createMessage(status = DeliveryStatus.SENDING)

        assertNotEquals(msg1, msg2)
    }

    @Test
    fun `toString contains essential info`() {
        val msg = createMessage()
        val str = msg.toString()

        assertTrue(str.contains("id=1"))
        assertTrue(str.contains(peer.toString()))
        assertTrue(str.contains("${payload.size} bytes"))
        assertTrue(str.contains("PENDING"))
    }

    @Test
    fun `nullable timestamp fields handled correctly`() {
        val msgNoAttempt = createMessage(lastAttemptAt = null, deliveredAt = null)
        val msgWithAttempt = createMessage(lastAttemptAt = now, deliveredAt = null)
        val msgDelivered = createMessage(
            status = DeliveryStatus.DELIVERED,
            lastAttemptAt = now,
            deliveredAt = now + 100
        )

        assertNull(msgNoAttempt.lastAttemptAt)
        assertNull(msgNoAttempt.deliveredAt)
        assertNotNull(msgWithAttempt.lastAttemptAt)
        assertNull(msgWithAttempt.deliveredAt)
        assertNotNull(msgDelivered.deliveredAt)
    }

    @Test
    fun `self equality returns true`() {
        val msg = createMessage()
        assertEquals(msg, msg)
    }

    @Test
    fun `equality with null returns false`() {
        val msg = createMessage()
        assertNotEquals(msg, null)
    }

    @Test
    fun `equality with different type returns false`() {
        val msg = createMessage()
        assertNotEquals(msg, "not a message")
    }

    private fun createMessage(
        id: Long = 1,
        peer: InetSocketAddress = this.peer,
        payload: ByteArray = this.payload,
        status: DeliveryStatus = DeliveryStatus.PENDING,
        retryCount: Int = 0,
        createdAt: Long = now,
        lastAttemptAt: Long? = null,
        deliveredAt: Long? = null
    ) = OutboundMessage(
        id = id,
        peer = peer,
        payload = payload,
        status = status,
        retryCount = retryCount,
        createdAt = createdAt,
        lastAttemptAt = lastAttemptAt,
        deliveredAt = deliveredAt
    )
}
