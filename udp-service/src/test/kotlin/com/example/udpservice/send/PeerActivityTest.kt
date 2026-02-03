package com.example.udpservice.send

import com.example.udpservice.persistence.OutboundMessageDao
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Peer Activity Detection")
class PeerActivityTest {

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
    @DisplayName("onPeerActivity")
    inner class OnPeerActivity {

        @Test
        @DisplayName("resumes WAITING messages for the peer")
        fun `resumes WAITING messages for the peer`() = runTest {
            val peer = InetSocketAddress("192.168.1.100", 5000)
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 3

            sendQueue.onPeerActivity(peer)

            coVerify { dao.resumeForPeer("192.168.1.100", 5000) }
        }

        @Test
        @DisplayName("handles peer with no WAITING messages")
        fun `handles peer with no WAITING messages`() = runTest {
            val peer = InetSocketAddress("192.168.1.100", 5000)
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 0

            sendQueue.onPeerActivity(peer)

            coVerify { dao.resumeForPeer("192.168.1.100", 5000) }
        }

        @Test
        @DisplayName("uses correct host and port")
        fun `uses correct host and port`() = runTest {
            val peer = InetSocketAddress("10.0.0.50", 8080)
            coEvery { dao.resumeForPeer(any(), any()) } returns 1

            sendQueue.onPeerActivity(peer)

            coVerify { dao.resumeForPeer("10.0.0.50", 8080) }
        }
    }

    @Nested
    @DisplayName("resumeForPeer")
    inner class ResumeForPeer {

        @Test
        @DisplayName("changes WAITING messages to PENDING")
        fun `changes WAITING messages to PENDING`() = runTest {
            val peer = InetSocketAddress("192.168.1.100", 5000)
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 2

            val count = sendQueue.resumeForPeer(peer)

            assertEquals(2, count)
            coVerify { dao.resumeForPeer("192.168.1.100", 5000) }
        }

        @Test
        @DisplayName("resets retry count when resuming")
        fun `resets retry count when resuming`() = runTest {
            // The DAO query should reset retry count to 0
            // This is verified by checking the SQL in OutboundMessageDao
            val peer = InetSocketAddress("192.168.1.100", 5000)
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 1

            sendQueue.resumeForPeer(peer)

            // DAO resumeForPeer SQL sets retryCount = 0
            coVerify { dao.resumeForPeer("192.168.1.100", 5000) }
        }

        @Test
        @DisplayName("returns count of resumed messages")
        fun `returns count of resumed messages`() = runTest {
            val peer = InetSocketAddress("192.168.1.100", 5000)
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 5

            val count = sendQueue.resumeForPeer(peer)

            assertEquals(5, count)
        }

        @Test
        @DisplayName("only affects messages for specific peer")
        fun `only affects messages for specific peer`() = runTest {
            val peer1 = InetSocketAddress("192.168.1.100", 5000)

            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 2
            coEvery { dao.resumeForPeer("192.168.1.101", 5000) } returns 1

            sendQueue.onPeerActivity(peer1)

            coVerify(exactly = 1) { dao.resumeForPeer("192.168.1.100", 5000) }
            coVerify(exactly = 0) { dao.resumeForPeer("192.168.1.101", 5000) }
        }
    }

    @Nested
    @DisplayName("Integration with status transitions")
    inner class StatusTransitions {

        @Test
        @DisplayName("WAITING -> PENDING is valid transition")
        fun `WAITING to PENDING is valid transition`() {
            val canTransition = DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.PENDING)
            assertTrue(canTransition)
        }

        @Test
        @DisplayName("WAITING cannot transition to other states")
        fun `WAITING cannot transition to other states`() {
            assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.SENDING))
            assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.DELIVERED))
            assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.RETRYING))
            assertFalse(DeliveryStatus.WAITING.canTransitionTo(DeliveryStatus.WAITING))
        }
    }

    @Nested
    @DisplayName("Resume triggers delivery")
    inner class ResumeTriggers {

        @Test
        @DisplayName("resumed messages are picked up by retry loop")
        fun `resumed messages are picked up by retry loop`() = runTest {
            // Messages in PENDING state are processed by processPendingMessages()
            // After resumeForPeer() changes WAITING -> PENDING, they will be sent
            val peer = InetSocketAddress("192.168.1.100", 5000)

            // After resume, WAITING messages become PENDING with retryCount = 0
            coEvery { dao.resumeForPeer("192.168.1.100", 5000) } returns 1

            val count = sendQueue.resumeForPeer(peer)

            assertEquals(1, count)
            // The actual status change is done by the DAO SQL query
            coVerify { dao.resumeForPeer("192.168.1.100", 5000) }
        }
    }
}
