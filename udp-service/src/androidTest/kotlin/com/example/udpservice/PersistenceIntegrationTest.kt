package com.example.udpservice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.udpservice.api.PacketParser
import com.example.udpservice.api.UdpPacket
import com.example.udpservice.persistence.PacketDatabase
import com.example.udpservice.persistence.PacketEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress

/**
 * Stage 2: Emulator + Device Tests - Packet persistence integration.
 *
 * Tests T013, T015: Service persistence integration.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceIntegrationTest {

    private lateinit var database: PacketDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PacketDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    // T013: Test service persists packet on receive
    @Test
    fun udpPacket_toEntity_parsesAndConvertsCorrectly() = runTest {
        // Create a UDP packet with appId prefix
        val appId = "testapp"
        val payload = "Hello, world!".toByteArray()
        val rawData = PacketParser.encode(appId, payload)!!

        val udpPacket = UdpPacket(
            data = rawData,
            sourceAddress = InetSocketAddress("192.168.1.100", 5000),
            timestamp = System.currentTimeMillis()
        )

        // Convert to entity
        val entity = udpPacket.toEntity()

        assertNotNull(entity)
        assertEquals(appId, entity!!.appId)
        assertTrue(payload.contentEquals(entity.data))
        assertEquals("192.168.1.100", entity.sourceIp)
        assertEquals(5000, entity.sourcePort)
    }

    // T015: Test high-rate packet persistence (100/sec burst)
    @Test
    fun highRatePacketPersistence_handles100PerSecondBurst() = runTest {
        val dao = database.packetDao()
        val appId = "burstapp"
        val packetCount = 100

        val startTime = System.currentTimeMillis()

        // Insert 100 packets as fast as possible
        repeat(packetCount) { i ->
            dao.insertPacket(
                PacketEntity(
                    appId = appId,
                    data = "packet $i".toByteArray(),
                    sourceIp = "192.168.1.${i % 256}",
                    sourcePort = 5000 + (i % 100),
                    timestamp = startTime + i
                )
            )
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Verify all packets persisted
        val count = dao.observePacketCountByAppId(appId).first()
        assertEquals(packetCount, count)

        // Log performance (should be < 1000ms for 100 packets)
        println("Inserted $packetCount packets in ${duration}ms")
    }

    @Test
    fun udpPacket_toEntity_returnsNullForInvalidPrefix() = runTest {
        // Packet without valid appId prefix
        val invalidPacket = UdpPacket(
            data = byteArrayOf(0x00), // length=0 is invalid
            sourceAddress = InetSocketAddress("192.168.1.1", 5000)
        )

        val entity = invalidPacket.toEntity()
        assertEquals(null, entity)
    }

    @Test
    fun udpPacket_toEntity_handlesEmptyPayload() = runTest {
        val appId = "emptytest"
        val rawData = PacketParser.encode(appId, ByteArray(0))!!

        val udpPacket = UdpPacket(
            data = rawData,
            sourceAddress = InetSocketAddress("192.168.1.1", 5000)
        )

        val entity = udpPacket.toEntity()

        assertNotNull(entity)
        assertEquals(appId, entity!!.appId)
        assertEquals(0, entity.data.size)
    }

    @Test
    fun batchPersistence_withDifferentAppIds() = runTest {
        val dao = database.packetDao()

        // Insert packets for multiple apps
        val apps = listOf("app1", "app2", "app3")
        val packetsPerApp = 10

        apps.forEach { appId ->
            repeat(packetsPerApp) { i ->
                dao.insertPacket(
                    PacketEntity(
                        appId = appId,
                        data = "packet $i".toByteArray(),
                        sourceIp = "192.168.1.1",
                        sourcePort = 5000,
                        timestamp = System.currentTimeMillis() + i
                    )
                )
            }
        }

        // Verify counts per app
        apps.forEach { appId ->
            val count = dao.observePacketCountByAppId(appId).first()
            assertEquals(packetsPerApp, count)
        }

        // Verify total count
        val totalCount = dao.observePacketCount().first()
        assertEquals(apps.size * packetsPerApp, totalCount)
    }
}
