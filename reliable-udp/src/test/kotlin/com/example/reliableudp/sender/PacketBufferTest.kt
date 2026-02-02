package com.example.reliableudp.sender

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress

class PacketBufferTest {

    private lateinit var buffer: PacketBufferImpl
    private val testDestination = InetSocketAddress("127.0.0.1", 5000)

    @BeforeEach
    fun setup() {
        buffer = PacketBufferImpl()
    }

    private fun createPacket(
        seqNum: Long,
        messageId: Int = 1,
        fragmentIndex: Int = 1,
        fragmentTotal: Int = 1
    ) = SentPacket(
        sequenceNumber = seqNum,
        messageId = messageId,
        fragmentIndex = fragmentIndex,
        fragmentTotal = fragmentTotal,
        payload = ByteArray(100),
        destination = testDestination
    )

    @Test
    fun `initially empty`() {
        assertEquals(0, buffer.size)
    }

    @Test
    fun `put increases size`() {
        buffer.put(createPacket(1))
        assertEquals(1, buffer.size)

        buffer.put(createPacket(2))
        assertEquals(2, buffer.size)
    }

    @Test
    fun `remove returns packet and decreases size`() {
        val packet = createPacket(1)
        buffer.put(packet)

        val removed = buffer.remove(1)
        assertEquals(packet.sequenceNumber, removed?.sequenceNumber)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `remove returns null for non-existent sequence number`() {
        buffer.put(createPacket(1))
        assertNull(buffer.remove(999))
    }

    @Test
    fun `getOlderThan returns packets exceeding timeout`() {
        val oldPacket = createPacket(1)
        buffer.put(oldPacket)

        // Sleep briefly to ensure packet ages
        Thread.sleep(50)

        val newPacket = createPacket(2)
        buffer.put(newPacket)

        // Get packets older than 25ms
        val old = buffer.getOlderThan(25_000_000L)
        assertEquals(1, old.size)
        assertEquals(1L, old[0].sequenceNumber)
    }

    @Test
    fun `getOlderThan does not remove packets`() {
        buffer.put(createPacket(1))
        Thread.sleep(50)

        buffer.getOlderThan(25_000_000L)
        assertEquals(1, buffer.size)
    }

    @Test
    fun `getOlderThan returns empty list when no old packets`() {
        buffer.put(createPacket(1))

        val old = buffer.getOlderThan(Long.MAX_VALUE)
        assertTrue(old.isEmpty())
    }

    @Test
    fun `removeByMessageId removes all packets for message`() {
        buffer.put(createPacket(1, messageId = 100, fragmentIndex = 1, fragmentTotal = 3))
        buffer.put(createPacket(2, messageId = 100, fragmentIndex = 2, fragmentTotal = 3))
        buffer.put(createPacket(3, messageId = 100, fragmentIndex = 3, fragmentTotal = 3))
        buffer.put(createPacket(4, messageId = 200))

        val removed = buffer.removeByMessageId(100)
        assertEquals(3, removed.size)
        assertEquals(1, buffer.size) // Only messageId=200 remains
    }

    @Test
    fun `removeByMessageId returns empty list for unknown messageId`() {
        buffer.put(createPacket(1, messageId = 100))

        val removed = buffer.removeByMessageId(999)
        assertTrue(removed.isEmpty())
        assertEquals(1, buffer.size)
    }

    @Test
    fun `hasPendingPackets returns true when packets exist`() {
        buffer.put(createPacket(1, messageId = 100))
        assertTrue(buffer.hasPendingPackets(100))
    }

    @Test
    fun `hasPendingPackets returns false when no packets exist`() {
        buffer.put(createPacket(1, messageId = 100))
        assertFalse(buffer.hasPendingPackets(999))
    }

    @Test
    fun `hasPendingPackets returns false after removal`() {
        buffer.put(createPacket(1, messageId = 100))
        buffer.removeByMessageId(100)
        assertFalse(buffer.hasPendingPackets(100))
    }

    @Test
    fun `concurrent put and remove is thread-safe`() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    val seqNum = (threadId * 1000 + i).toLong()
                    buffer.put(createPacket(seqNum))
                    buffer.remove(seqNum)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(0, buffer.size)
    }
}
