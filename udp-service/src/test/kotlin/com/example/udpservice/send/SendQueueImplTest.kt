package com.example.udpservice.send

import com.example.udpservice.persistence.OutboundMessageDao
import com.example.udpservice.persistence.OutboundMessageEntity
import io.mockk.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@DisplayName("SendQueueImpl")
class SendQueueImplTest {

    private lateinit var dao: OutboundMessageDao
    private lateinit var sendQueue: SendQueueImpl

    @BeforeEach
    fun setup() {
        dao = mockk(relaxed = true)
        sendQueue = SendQueueImpl(dao)
    }

    @AfterEach
    fun teardown() {
        sendQueue.stop()
        unmockkAll()
    }

    @Nested
    @DisplayName("enqueue")
    inner class Enqueue {

        @Test
        @DisplayName("persists message before returning")
        fun `persists message before returning`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val payload = "Hello".toByteArray()

            coEvery { dao.insert(any()) } returns 1L

            val result = sendQueue.enqueue(peer, payload)

            assertTrue(result is SendResult.Queued)
            assertEquals(1L, (result as SendResult.Queued).messageId)

            coVerify {
                dao.insert(match {
                    it.peerHost == "192.168.1.1" &&
                    it.peerPort == 5000 &&
                    it.payload.contentEquals(payload) &&
                    it.status == DeliveryStatus.PENDING
                })
            }
        }

        @Test
        @DisplayName("returns messageId from database")
        fun `returns messageId from database`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)

            coEvery { dao.insert(any()) } returns 42L

            val result = sendQueue.enqueue(peer, ByteArray(0))

            assertTrue(result is SendResult.Queued)
            assertEquals(42L, (result as SendResult.Queued).messageId)
        }

        @Test
        @DisplayName("creates entity with PENDING status")
        fun `creates entity with PENDING status`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.insert(any()) } returns 1L

            sendQueue.enqueue(peer, ByteArray(0))

            coVerify {
                dao.insert(match { it.status == DeliveryStatus.PENDING })
            }
        }

        @Test
        @DisplayName("creates entity with zero retry count")
        fun `creates entity with zero retry count`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.insert(any()) } returns 1L

            sendQueue.enqueue(peer, ByteArray(0))

            coVerify {
                dao.insert(match { it.retryCount == 0 })
            }
        }

        @Test
        @DisplayName("sets createdAt timestamp")
        fun `sets createdAt timestamp`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val beforeTime = System.currentTimeMillis()

            coEvery { dao.insert(any()) } returns 1L

            sendQueue.enqueue(peer, ByteArray(0))

            val afterTime = System.currentTimeMillis()

            coVerify {
                dao.insert(match {
                    it.createdAt >= beforeTime && it.createdAt <= afterTime
                })
            }
        }

        @Test
        @DisplayName("fails if payload exceeds 64KB")
        fun `fails if payload exceeds 64KB`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val largePayload = ByteArray(65537) // 64KB + 1

            val result = sendQueue.enqueue(peer, largePayload)

            assertTrue(result is SendResult.Failed)
            assertTrue((result as SendResult.Failed).reason.contains("64KB"))
            coVerify(exactly = 0) { dao.insert(any()) }
        }

        @Test
        @DisplayName("allows exactly 64KB payload")
        fun `allows exactly 64KB payload`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val maxPayload = ByteArray(65536) // Exactly 64KB

            coEvery { dao.insert(any()) } returns 1L

            val result = sendQueue.enqueue(peer, maxPayload)

            assertTrue(result is SendResult.Queued)
            coVerify { dao.insert(any()) }
        }

        @Test
        @DisplayName("returns failure on database error")
        fun `returns failure on database error`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.insert(any()) } throws RuntimeException("Database error")

            val result = sendQueue.enqueue(peer, ByteArray(0))

            assertTrue(result is SendResult.Failed)
        }
    }

    @Nested
    @DisplayName("get")
    inner class Get {

        @Test
        @DisplayName("returns message when found")
        fun `returns message when found`() = runTest {
            val entity = OutboundMessageEntity(
                id = 1L,
                peerHost = "192.168.1.1",
                peerPort = 5000,
                payload = "Hello".toByteArray(),
                status = DeliveryStatus.PENDING,
                createdAt = 1000L
            )
            coEvery { dao.getById(1L) } returns entity

            val message = sendQueue.get(1L)

            assertNotNull(message)
            assertEquals(1L, message?.id)
            assertEquals("192.168.1.1", message?.peer?.hostString)
            assertEquals(5000, message?.peer?.port)
        }

        @Test
        @DisplayName("returns null when not found")
        fun `returns null when not found`() = runTest {
            coEvery { dao.getById(999L) } returns null

            val message = sendQueue.get(999L)

            assertNull(message)
        }
    }

    @Nested
    @DisplayName("getPendingForPeer")
    inner class GetPendingForPeer {

        @Test
        @DisplayName("returns pending messages for peer")
        fun `returns pending messages for peer`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val entities = listOf(
                OutboundMessageEntity(
                    id = 1L,
                    peerHost = "192.168.1.1",
                    peerPort = 5000,
                    payload = "Test".toByteArray(),
                    status = DeliveryStatus.PENDING,
                    createdAt = 1000L
                )
            )
            coEvery { dao.getPendingForPeer("192.168.1.1", 5000) } returns entities

            val messages = sendQueue.getPendingForPeer(peer)

            assertEquals(1, messages.size)
            assertEquals(1L, messages[0].id)
        }

        @Test
        @DisplayName("returns empty list when no pending messages")
        fun `returns empty list when no pending messages`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.getPendingForPeer("192.168.1.1", 5000) } returns emptyList()

            val messages = sendQueue.getPendingForPeer(peer)

            assertTrue(messages.isEmpty())
        }
    }

    @Nested
    @DisplayName("updateStatus")
    inner class UpdateStatus {

        @Test
        @DisplayName("updates status in database")
        fun `updates status in database`() = runTest {
            coEvery { dao.updateStatus(1L, DeliveryStatus.SENDING) } just Runs

            sendQueue.updateStatus(1L, DeliveryStatus.SENDING)

            coVerify { dao.updateStatus(1L, DeliveryStatus.SENDING) }
        }

        @Test
        @DisplayName("updates status with retry count")
        fun `updates status with retry count`() = runTest {
            coEvery { dao.updateStatusWithRetry(any(), any(), any(), any()) } just Runs

            sendQueue.updateStatus(1L, DeliveryStatus.RETRYING, retryCount = 3)

            coVerify {
                dao.updateStatusWithRetry(1L, DeliveryStatus.RETRYING, 3, any())
            }
        }
    }

    @Nested
    @DisplayName("markDelivered")
    inner class MarkDelivered {

        @Test
        @DisplayName("marks message as delivered with timestamp")
        fun `marks message as delivered with timestamp`() = runTest {
            val beforeTime = System.currentTimeMillis()
            coEvery { dao.markDelivered(any(), any()) } just Runs

            sendQueue.markDelivered(1L)

            val afterTime = System.currentTimeMillis()

            coVerify {
                dao.markDelivered(1L, match { it in beforeTime..afterTime })
            }
        }
    }

    @Nested
    @DisplayName("cancel")
    inner class Cancel {

        @Test
        @DisplayName("deletes message and returns true")
        fun `deletes message and returns true`() = runTest {
            coEvery { dao.getById(1L) } returns OutboundMessageEntity(
                id = 1L,
                peerHost = "192.168.1.1",
                peerPort = 5000,
                payload = ByteArray(0),
                status = DeliveryStatus.PENDING,
                createdAt = 1000L
            )
            coEvery { dao.delete(1L) } returns 1

            val cancelled = sendQueue.cancel(1L)

            assertTrue(cancelled)
            coVerify { dao.delete(1L) }
        }

        @Test
        @DisplayName("returns false when message not found")
        fun `returns false when message not found`() = runTest {
            coEvery { dao.getById(1L) } returns null

            val cancelled = sendQueue.cancel(1L)

            assertFalse(cancelled)
        }

        @Test
        @DisplayName("returns false when message already delivered")
        fun `returns false when message already delivered`() = runTest {
            coEvery { dao.getById(1L) } returns OutboundMessageEntity(
                id = 1L,
                peerHost = "192.168.1.1",
                peerPort = 5000,
                payload = ByteArray(0),
                status = DeliveryStatus.DELIVERED,
                createdAt = 1000L,
                deliveredAt = 2000L
            )

            val cancelled = sendQueue.cancel(1L)

            assertFalse(cancelled)
            coVerify(exactly = 0) { dao.delete(any()) }
        }
    }

    @Nested
    @DisplayName("resumeForPeer")
    inner class ResumeForPeer {

        @Test
        @DisplayName("resumes waiting messages for peer")
        fun `resumes waiting messages for peer`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.resumeForPeer("192.168.1.1", 5000) } returns 3

            val count = sendQueue.resumeForPeer(peer)

            assertEquals(3, count)
            coVerify { dao.resumeForPeer("192.168.1.1", 5000) }
        }
    }

    @Nested
    @DisplayName("observeAll")
    inner class ObserveAll {

        @Test
        @DisplayName("emits messages from dao")
        fun `emits messages from dao`() = runTest {
            val entities = listOf(
                OutboundMessageEntity(
                    id = 1L,
                    peerHost = "192.168.1.1",
                    peerPort = 5000,
                    payload = ByteArray(0),
                    status = DeliveryStatus.PENDING,
                    createdAt = 1000L
                )
            )
            every { dao.observeAll() } returns flowOf(entities)

            val messages = sendQueue.observeAll().first()

            assertEquals(1, messages.size)
            assertEquals(1L, messages[0].id)
        }
    }

    @Nested
    @DisplayName("observeForPeer")
    inner class ObserveForPeer {

        @Test
        @DisplayName("emits messages for specific peer")
        fun `emits messages for specific peer`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            val entities = listOf(
                OutboundMessageEntity(
                    id = 1L,
                    peerHost = "192.168.1.1",
                    peerPort = 5000,
                    payload = ByteArray(0),
                    status = DeliveryStatus.PENDING,
                    createdAt = 1000L
                )
            )
            every { dao.observeForPeer("192.168.1.1", 5000) } returns flowOf(entities)

            val messages = sendQueue.observeForPeer(peer).first()

            assertEquals(1, messages.size)
        }
    }

    @Nested
    @DisplayName("onPeerActivity")
    inner class OnPeerActivity {

        @Test
        @DisplayName("resumes waiting messages for peer")
        fun `resumes waiting messages for peer`() = runTest {
            val peer = InetSocketAddress("192.168.1.1", 5000)
            coEvery { dao.resumeForPeer("192.168.1.1", 5000) } returns 2

            sendQueue.onPeerActivity(peer)

            coVerify { dao.resumeForPeer("192.168.1.1", 5000) }
        }
    }
}
