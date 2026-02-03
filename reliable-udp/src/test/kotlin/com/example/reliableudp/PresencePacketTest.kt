package com.example.reliableudp

import com.example.reliableudp.protocol.Header
import com.example.reliableudp.protocol.PacketType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress

class PresencePacketTest {

    @Test
    fun `presence packet has correct header format`() {
        val header = Header(
            type = PacketType.PRESENCE,
            messageId = 0,
            sequenceNumber = 0,
            fragmentIndex = 1,
            fragmentTotal = 1
        )

        val bytes = header.toBytes()
        assertEquals(ReliableUdpConstants.HEADER_SIZE, bytes.size)
        assertEquals(PacketType.PRESENCE.value, bytes[0])
    }

    @Test
    fun `presence header parses correctly`() {
        val header = Header(
            type = PacketType.PRESENCE,
            messageId = 0,
            sequenceNumber = 0,
            fragmentIndex = 1,
            fragmentTotal = 1
        )

        val bytes = header.toBytes()
        val parsed = Header.fromBytes(bytes)

        assertNotNull(parsed)
        assertEquals(PacketType.PRESENCE, parsed!!.type)
        assertEquals(0, parsed.messageId)
        assertEquals(0L, parsed.sequenceNumber)
        assertEquals(1, parsed.fragmentIndex)
        assertEquals(1, parsed.fragmentTotal)
    }

    @Test
    fun `presence packet has zero-length payload`() {
        val header = Header(
            type = PacketType.PRESENCE,
            messageId = 0,
            sequenceNumber = 0,
            fragmentIndex = 1,
            fragmentTotal = 1
        )

        // Presence is header-only, no additional payload
        val packet = header.toBytes()
        assertEquals(ReliableUdpConstants.HEADER_SIZE, packet.size)
    }

    @Test
    fun `ReceivedMessage can be created with isPresence flag`() {
        val source = InetSocketAddress("192.168.1.100", 5000)
        val presenceMessage = ReceivedMessage(
            payload = ByteArray(0),
            source = source,
            isPresence = true
        )

        assertTrue(presenceMessage.isPresence)
        assertEquals(0, presenceMessage.payload.size)
        assertEquals(source, presenceMessage.source)
    }

    @Test
    fun `ReceivedMessage defaults isPresence to false`() {
        val source = InetSocketAddress("192.168.1.100", 5000)
        val dataMessage = ReceivedMessage(
            payload = "hello".toByteArray(),
            source = source
        )

        assertFalse(dataMessage.isPresence)
    }

    @Test
    fun `presence and data messages are distinguished by flag`() {
        val source = InetSocketAddress("192.168.1.100", 5000)
        val presence = ReceivedMessage(ByteArray(0), source, isPresence = true)
        val data = ReceivedMessage("hello".toByteArray(), source, isPresence = false)

        assertTrue(presence.isPresence)
        assertFalse(data.isPresence)
        assertNotEquals(presence, data)
    }
}
