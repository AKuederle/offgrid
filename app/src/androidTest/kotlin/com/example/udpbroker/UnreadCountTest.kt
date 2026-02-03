package com.example.udpbroker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.udpservice.persistence.PacketDatabase
import com.example.udpservice.persistence.PacketEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for unread count tracking (T046).
 *
 * Tests that unread counts are accurate and update correctly when
 * messages are marked as read.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class UnreadCountTest {

    private lateinit var database: PacketDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        database = PacketDatabase.getInstance(context)
        runTest {
            database.packetDao().deleteAllPackets()
        }
    }

    @After
    fun teardown() {
        runTest {
            database.packetDao().deleteAllPackets()
        }
    }

    @Test
    fun unreadCount_tracksNewMessages() = runTest {
        val dao = database.packetDao()

        // Insert 3 broker messages and 2 alerts messages
        dao.insertPacket(createPacket("broker", "Message 1"))
        dao.insertPacket(createPacket("broker", "Message 2"))
        dao.insertPacket(createPacket("broker", "Message 3"))
        dao.insertPacket(createPacket("alerts", "Alert 1"))
        dao.insertPacket(createPacket("alerts", "Alert 2"))

        // Verify unread counts
        val brokerUnread = dao.getUnreadCount("broker")
        val alertsUnread = dao.getUnreadCount("alerts")

        assertEquals("Broker should have 3 unread", 3, brokerUnread)
        assertEquals("Alerts should have 2 unread", 2, alertsUnread)
    }

    @Test
    fun markAllAsRead_updatesUnreadCount() = runTest {
        val dao = database.packetDao()

        // Insert messages
        dao.insertPacket(createPacket("broker", "Message 1"))
        dao.insertPacket(createPacket("broker", "Message 2"))
        dao.insertPacket(createPacket("alerts", "Alert 1"))

        // Mark broker messages as read
        val markedCount = dao.markAllAsRead("broker")

        assertEquals("Should mark 2 messages as read", 2, markedCount)
        assertEquals("Broker should have 0 unread", 0, dao.getUnreadCount("broker"))
        assertEquals("Alerts should still have 1 unread", 1, dao.getUnreadCount("alerts"))
    }

    @Test
    fun observeUnreadCount_emitsUpdates() = runTest {
        val dao = database.packetDao()

        // Initial count should be 0
        assertEquals(0, dao.observeUnreadCount("broker").first())

        // Insert a message
        dao.insertPacket(createPacket("broker", "Message 1"))

        // Count should update
        assertEquals(1, dao.observeUnreadCount("broker").first())

        // Mark as read
        dao.markAllAsRead("broker")

        // Count should be 0 again
        assertEquals(0, dao.observeUnreadCount("broker").first())
    }

    @Test
    fun messagesAreSeparatedByPrefix() = runTest {
        val dao = database.packetDao()

        // Insert messages to both prefixes
        dao.insertPacket(createPacket("broker", "Broker 1"))
        dao.insertPacket(createPacket("alerts", "Alert 1"))

        // Verify they're stored separately
        val brokerPackets = dao.observePacketsByAppId("broker").first()
        val alertsPackets = dao.observePacketsByAppId("alerts").first()

        assertEquals("Broker should have 1 message", 1, brokerPackets.size)
        assertEquals("Alerts should have 1 message", 1, alertsPackets.size)
        assertEquals("broker", brokerPackets[0].appId)
        assertEquals("alerts", alertsPackets[0].appId)
    }

    private fun createPacket(appId: String, message: String): PacketEntity {
        return PacketEntity(
            appId = appId,
            sourceIp = "192.168.1.100",
            sourcePort = 12345,
            data = message.toByteArray(),
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
    }
}
