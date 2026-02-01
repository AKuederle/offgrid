package com.example.udpservice.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 2: Emulator + Device Tests - PacketDao database operations.
 *
 * Tests run on Android device/emulator with in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class PacketDaoTest {

    private lateinit var database: PacketDatabase
    private lateinit var dao: PacketDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PacketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.packetDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // T004: Test for PacketEntity insert/retrieve
    @Test
    fun insertPacket_retrievableById() = runTest {
        val packet = PacketEntity(
            appId = "app1",
            data = "test data".toByteArray(),
            sourceIp = "192.168.1.100",
            sourcePort = 5000,
            timestamp = System.currentTimeMillis()
        )

        val id = dao.insertPacket(packet)
        val retrieved = dao.getPacketById(id)

        assertNotNull(retrieved)
        assertEquals(packet.appId, retrieved!!.appId)
        assertTrue(packet.data.contentEquals(retrieved.data))
        assertEquals(packet.sourceIp, retrieved.sourceIp)
        assertEquals(packet.sourcePort, retrieved.sourcePort)
        assertEquals(packet.timestamp, retrieved.timestamp)
    }

    // T005: Test for PacketEntity 64KB BLOB storage
    @Test
    fun insertPacket_stores64KBPayload() = runTest {
        // Create 64KB payload (65,507 bytes is max UDP payload minus prefix overhead)
        val largePayload = ByteArray(65500) { it.toByte() }
        val packet = PacketEntity(
            appId = "bigdata",
            data = largePayload,
            sourceIp = "10.0.0.1",
            sourcePort = 8080,
            timestamp = System.currentTimeMillis()
        )

        val id = dao.insertPacket(packet)
        val retrieved = dao.getPacketById(id)

        assertNotNull(retrieved)
        assertEquals(65500, retrieved!!.data.size)
        assertTrue(largePayload.contentEquals(retrieved.data))
    }

    // T006: Test for observePackets Flow emission
    @Test
    fun observePackets_emitsOnInsert() = runTest {
        val packet1 = PacketEntity(
            appId = "app1",
            data = "packet 1".toByteArray(),
            sourceIp = "192.168.1.1",
            sourcePort = 5000,
            timestamp = 1000L
        )
        val packet2 = PacketEntity(
            appId = "app1",
            data = "packet 2".toByteArray(),
            sourceIp = "192.168.1.2",
            sourcePort = 5001,
            timestamp = 2000L
        )

        dao.insertPacket(packet1)
        dao.insertPacket(packet2)

        val packets = dao.observePackets(limit = 100).first()

        assertEquals(2, packets.size)
        // Ordered by timestamp DESC (newest first)
        assertEquals(2000L, packets[0].timestamp)
        assertEquals(1000L, packets[1].timestamp)
    }

    // T007: Test for deleteAllPackets
    @Test
    fun deleteAllPackets_clearsDatabase() = runTest {
        // Insert several packets
        repeat(5) { i ->
            dao.insertPacket(
                PacketEntity(
                    appId = "app1",
                    data = "packet $i".toByteArray(),
                    sourceIp = "192.168.1.$i",
                    sourcePort = 5000 + i,
                    timestamp = System.currentTimeMillis() + i
                )
            )
        }

        // Verify packets exist
        val beforeDelete = dao.observePackets(limit = 100).first()
        assertEquals(5, beforeDelete.size)

        // Delete all
        val deletedCount = dao.deleteAllPackets()

        // Verify deletion
        assertEquals(5, deletedCount)
        val afterDelete = dao.observePackets(limit = 100).first()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun observePacketCount_returnsCorrectCount() = runTest {
        // Insert packets
        repeat(3) { i ->
            dao.insertPacket(
                PacketEntity(
                    appId = "app1",
                    data = "packet $i".toByteArray(),
                    sourceIp = "192.168.1.$i",
                    sourcePort = 5000,
                    timestamp = System.currentTimeMillis() + i
                )
            )
        }

        val count = dao.observePacketCount().first()
        assertEquals(3, count)
    }

    @Test
    fun insertPackets_batchInsert() = runTest {
        val packets = (1..10).map { i ->
            PacketEntity(
                appId = "app1",
                data = "batch packet $i".toByteArray(),
                sourceIp = "192.168.1.$i",
                sourcePort = 5000,
                timestamp = System.currentTimeMillis() + i
            )
        }

        dao.insertPackets(packets)

        val count = dao.observePacketCount().first()
        assertEquals(10, count)
    }

    @Test
    fun observePackets_respectsLimit() = runTest {
        // Insert 20 packets
        repeat(20) { i ->
            dao.insertPacket(
                PacketEntity(
                    appId = "app1",
                    data = "packet $i".toByteArray(),
                    sourceIp = "192.168.1.1",
                    sourcePort = 5000,
                    timestamp = System.currentTimeMillis() + i
                )
            )
        }

        val limitedPackets = dao.observePackets(limit = 10).first()
        assertEquals(10, limitedPackets.size)
    }

    // Test appId filtering
    @Test
    fun observePacketsByAppId_filtersCorrectly() = runTest {
        // Insert packets for different apps
        dao.insertPacket(
            PacketEntity(
                appId = "app1",
                data = "app1 packet 1".toByteArray(),
                sourceIp = "192.168.1.1",
                sourcePort = 5000,
                timestamp = 1000L
            )
        )
        dao.insertPacket(
            PacketEntity(
                appId = "app2",
                data = "app2 packet 1".toByteArray(),
                sourceIp = "192.168.1.2",
                sourcePort = 5000,
                timestamp = 2000L
            )
        )
        dao.insertPacket(
            PacketEntity(
                appId = "app1",
                data = "app1 packet 2".toByteArray(),
                sourceIp = "192.168.1.1",
                sourcePort = 5000,
                timestamp = 3000L
            )
        )

        val app1Packets = dao.observePacketsByAppId("app1").first()
        val app2Packets = dao.observePacketsByAppId("app2").first()

        assertEquals(2, app1Packets.size)
        assertEquals(1, app2Packets.size)
        assertTrue(app1Packets.all { it.appId == "app1" })
        assertTrue(app2Packets.all { it.appId == "app2" })
    }

    @Test
    fun observePacketCountByAppId_returnsCorrectCount() = runTest {
        dao.insertPacket(
            PacketEntity(appId = "app1", data = "a".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 1000L)
        )
        dao.insertPacket(
            PacketEntity(appId = "app2", data = "b".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 2000L)
        )
        dao.insertPacket(
            PacketEntity(appId = "app1", data = "c".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 3000L)
        )

        val app1Count = dao.observePacketCountByAppId("app1").first()
        val app2Count = dao.observePacketCountByAppId("app2").first()

        assertEquals(2, app1Count)
        assertEquals(1, app2Count)
    }

    @Test
    fun deletePacketsByAppId_deletesOnlyMatchingAppId() = runTest {
        dao.insertPacket(
            PacketEntity(appId = "app1", data = "a".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 1000L)
        )
        dao.insertPacket(
            PacketEntity(appId = "app2", data = "b".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 2000L)
        )
        dao.insertPacket(
            PacketEntity(appId = "app1", data = "c".toByteArray(), sourceIp = "1.1.1.1", sourcePort = 5000, timestamp = 3000L)
        )

        val deletedCount = dao.deletePacketsByAppId("app1")

        assertEquals(2, deletedCount)
        val remainingCount = dao.observePacketCount().first()
        assertEquals(1, remainingCount)
        val remaining = dao.observePackets().first()
        assertEquals("app2", remaining[0].appId)
    }
}
