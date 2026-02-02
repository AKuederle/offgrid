package com.example.reliableudp.protocol

import com.example.reliableudp.ReliableUdpConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class HeaderTest {

    @Test
    fun `header serializes to correct size`() {
        val header = Header(
            type = PacketType.DATA,
            messageId = 1,
            sequenceNumber = 100,
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        assertEquals(ReliableUdpConstants.HEADER_SIZE, header.toBytes().size)
    }

    @Test
    fun `header round-trips through serialization`() {
        val original = Header(
            type = PacketType.DATA,
            messageId = 12345,
            sequenceNumber = 67890,
            fragmentIndex = 3,
            fragmentTotal = 5
        )

        val bytes = original.toBytes()
        val parsed = Header.fromBytes(bytes)

        assertEquals(original, parsed)
    }

    @Test
    fun `header preserves all packet types`() {
        for (type in PacketType.entries) {
            val header = Header(type, 1, 1, 1, 1)
            val parsed = Header.fromBytes(header.toBytes())
            assertEquals(type, parsed?.type)
        }
    }

    @Test
    fun `header preserves large message ID`() {
        val header = Header(
            type = PacketType.DATA,
            messageId = Int.MAX_VALUE,
            sequenceNumber = 1,
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        val parsed = Header.fromBytes(header.toBytes())
        assertEquals(Int.MAX_VALUE, parsed?.messageId)
    }

    @Test
    fun `header preserves negative message ID`() {
        val header = Header(
            type = PacketType.DATA,
            messageId = -1,
            sequenceNumber = 1,
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        val parsed = Header.fromBytes(header.toBytes())
        assertEquals(-1, parsed?.messageId)
    }

    @Test
    fun `header preserves large sequence number`() {
        // Sequence number uses lower 32 bits in wire format
        val header = Header(
            type = PacketType.DATA,
            messageId = 1,
            sequenceNumber = 0xFFFFFFFFL, // Max unsigned 32-bit
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        val parsed = Header.fromBytes(header.toBytes())
        assertEquals(0xFFFFFFFFL, parsed?.sequenceNumber)
    }

    @Test
    fun `header preserves maximum fragment values`() {
        val header = Header(
            type = PacketType.DATA,
            messageId = 1,
            sequenceNumber = 1,
            fragmentIndex = 255,
            fragmentTotal = 255
        )
        val parsed = Header.fromBytes(header.toBytes())
        assertEquals(255, parsed?.fragmentIndex)
        assertEquals(255, parsed?.fragmentTotal)
    }

    @Test
    fun `fromBytes returns null for too short data`() {
        val shortData = ByteArray(ReliableUdpConstants.HEADER_SIZE - 1)
        assertNull(Header.fromBytes(shortData))
    }

    @Test
    fun `fromBytes returns null for invalid packet type`() {
        val data = ByteArray(ReliableUdpConstants.HEADER_SIZE)
        data[0] = 0xFF.toByte() // Invalid type
        assertNull(Header.fromBytes(data))
    }

    @Test
    fun `fromBytes returns null for zero fragment index`() {
        val header = Header(PacketType.DATA, 1, 1, 1, 1)
        val bytes = header.toBytes()
        bytes[9] = 0 // Set fragmentIndex to 0
        assertNull(Header.fromBytes(bytes))
    }

    @Test
    fun `fromBytes returns null for zero fragment total`() {
        val header = Header(PacketType.DATA, 1, 1, 1, 1)
        val bytes = header.toBytes()
        bytes[10] = 0 // Set fragmentTotal to 0
        assertNull(Header.fromBytes(bytes))
    }

    @Test
    fun `fromBytes returns null when fragment index exceeds total`() {
        val header = Header(PacketType.DATA, 1, 1, 1, 1)
        val bytes = header.toBytes()
        bytes[9] = 5  // fragmentIndex = 5
        bytes[10] = 3 // fragmentTotal = 3
        assertNull(Header.fromBytes(bytes))
    }

    @Test
    fun `fromBytes works with offset`() {
        val prefix = ByteArray(10) { 0xFF.toByte() }
        val header = Header(PacketType.ACK, 123, 456, 2, 4)
        val headerBytes = header.toBytes()
        val combined = prefix + headerBytes

        val parsed = Header.fromBytes(combined, offset = 10)
        assertEquals(header, parsed)
    }

    @Test
    fun `constructor rejects fragment index less than 1`() {
        assertThrows<IllegalArgumentException> {
            Header(PacketType.DATA, 1, 1, 0, 1)
        }
    }

    @Test
    fun `constructor rejects fragment total less than 1`() {
        assertThrows<IllegalArgumentException> {
            Header(PacketType.DATA, 1, 1, 1, 0)
        }
    }

    @Test
    fun `constructor rejects fragment index exceeding total`() {
        assertThrows<IllegalArgumentException> {
            Header(PacketType.DATA, 1, 1, 5, 3)
        }
    }

    @Test
    fun `constructor rejects negative sequence number`() {
        assertThrows<IllegalArgumentException> {
            Header(PacketType.DATA, 1, -1, 1, 1)
        }
    }

    @Test
    fun `constructor rejects fragment index exceeding 255`() {
        assertThrows<IllegalArgumentException> {
            Header(PacketType.DATA, 1, 1, 256, 256)
        }
    }

    @Test
    fun `wire format is big endian`() {
        val header = Header(
            type = PacketType.DATA,
            messageId = 0x01020304,
            sequenceNumber = 0x05060708,
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        val bytes = header.toBytes()

        // Check big-endian byte order for messageId (bytes 1-4)
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0x02.toByte(), bytes[2])
        assertEquals(0x03.toByte(), bytes[3])
        assertEquals(0x04.toByte(), bytes[4])

        // Check big-endian byte order for sequenceNumber (bytes 5-8)
        assertEquals(0x05.toByte(), bytes[5])
        assertEquals(0x06.toByte(), bytes[6])
        assertEquals(0x07.toByte(), bytes[7])
        assertEquals(0x08.toByte(), bytes[8])
    }
}
