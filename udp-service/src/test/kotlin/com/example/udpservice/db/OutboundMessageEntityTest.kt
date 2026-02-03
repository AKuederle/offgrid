package com.example.udpservice.db

import com.example.udpservice.persistence.OutboundMessageEntity
import com.example.udpservice.send.DeliveryStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OutboundMessageEntityTest {

    @Test
    fun `entity creates with required fields`() {
        val entity = OutboundMessageEntity(
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = "Hello".toByteArray(),
            status = DeliveryStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )

        assertEquals("192.168.1.1", entity.peerHost)
        assertEquals(5000, entity.peerPort)
        assertArrayEquals("Hello".toByteArray(), entity.payload)
        assertEquals(DeliveryStatus.PENDING, entity.status)
        assertEquals(0, entity.retryCount)
        assertNull(entity.lastAttemptAt)
        assertNull(entity.deliveredAt)
    }

    @Test
    fun `entity id defaults to zero for auto-generation`() {
        val entity = OutboundMessageEntity(
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.PENDING,
            createdAt = 1000L
        )

        assertEquals(0L, entity.id)
    }

    @Test
    fun `entity with same values are equal`() {
        val now = System.currentTimeMillis()
        val payload = "Test".toByteArray()

        val entity1 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = payload.copyOf(),
            status = DeliveryStatus.SENDING,
            retryCount = 2,
            createdAt = now,
            lastAttemptAt = now + 1000
        )

        val entity2 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = payload.copyOf(),
            status = DeliveryStatus.SENDING,
            retryCount = 2,
            createdAt = now,
            lastAttemptAt = now + 1000
        )

        assertEquals(entity1, entity2)
        assertEquals(entity1.hashCode(), entity2.hashCode())
    }

    @Test
    fun `entity with different id are not equal`() {
        val now = System.currentTimeMillis()

        val entity1 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.PENDING,
            createdAt = now
        )

        val entity2 = OutboundMessageEntity(
            id = 2,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.PENDING,
            createdAt = now
        )

        assertNotEquals(entity1, entity2)
    }

    @Test
    fun `entity with different payload are not equal`() {
        val now = System.currentTimeMillis()

        val entity1 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = "Hello".toByteArray(),
            status = DeliveryStatus.PENDING,
            createdAt = now
        )

        val entity2 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = "World".toByteArray(),
            status = DeliveryStatus.PENDING,
            createdAt = now
        )

        assertNotEquals(entity1, entity2)
    }

    @Test
    fun `entity with different status are not equal`() {
        val now = System.currentTimeMillis()

        val entity1 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.PENDING,
            createdAt = now
        )

        val entity2 = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.SENDING,
            createdAt = now
        )

        assertNotEquals(entity1, entity2)
    }

    @Test
    fun `entity can store maximum payload size`() {
        val maxPayload = ByteArray(65536) { it.toByte() }

        val entity = OutboundMessageEntity(
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = maxPayload,
            status = DeliveryStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )

        assertEquals(65536, entity.payload.size)
        assertArrayEquals(maxPayload, entity.payload)
    }

    @Test
    fun `entity supports all delivery statuses`() {
        val now = System.currentTimeMillis()

        for (status in DeliveryStatus.entries) {
            val entity = OutboundMessageEntity(
                peerHost = "192.168.1.1",
                peerPort = 5000,
                payload = ByteArray(0),
                status = status,
                createdAt = now
            )
            assertEquals(status, entity.status)
        }
    }

    @Test
    fun `copy updates specified fields`() {
        val original = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = "Test".toByteArray(),
            status = DeliveryStatus.PENDING,
            retryCount = 0,
            createdAt = 1000L
        )

        val updated = original.copy(
            status = DeliveryStatus.SENDING,
            lastAttemptAt = 2000L
        )

        assertEquals(1L, updated.id)
        assertEquals(DeliveryStatus.SENDING, updated.status)
        assertEquals(2000L, updated.lastAttemptAt)
        assertEquals(0, updated.retryCount)
    }

    @Test
    fun `delivered entity has deliveredAt set`() {
        val entity = OutboundMessageEntity(
            id = 1,
            peerHost = "192.168.1.1",
            peerPort = 5000,
            payload = ByteArray(0),
            status = DeliveryStatus.DELIVERED,
            createdAt = 1000L,
            lastAttemptAt = 2000L,
            deliveredAt = 2500L
        )

        assertEquals(DeliveryStatus.DELIVERED, entity.status)
        assertNotNull(entity.deliveredAt)
        assertEquals(2500L, entity.deliveredAt)
    }
}
