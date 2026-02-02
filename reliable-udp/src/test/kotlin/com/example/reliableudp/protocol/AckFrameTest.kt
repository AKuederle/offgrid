package com.example.reliableudp.protocol

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class AckRangeTest {

    @Test
    fun `contains returns true for values within range`() {
        val range = AckRange(5, 10)
        assertTrue(range.contains(5))
        assertTrue(range.contains(7))
        assertTrue(range.contains(10))
    }

    @Test
    fun `contains returns false for values outside range`() {
        val range = AckRange(5, 10)
        assertFalse(range.contains(4))
        assertFalse(range.contains(11))
    }

    @Test
    fun `size is correct for range`() {
        assertEquals(1, AckRange(5, 5).size)
        assertEquals(6, AckRange(5, 10).size)
        assertEquals(100, AckRange(0, 99).size)
    }

    @Test
    fun `constructor rejects smallest greater than largest`() {
        assertThrows<IllegalArgumentException> {
            AckRange(10, 5)
        }
    }

    @Test
    fun `single element range is valid`() {
        val range = AckRange(42, 42)
        assertEquals(1, range.size)
        assertTrue(range.contains(42))
    }
}

class AckFrameTest {

    @Test
    fun `single contiguous range round-trips`() {
        val frame = AckFrame(
            largestAcked = 10,
            ackDelayMicros = 1000,
            ranges = listOf(AckRange(1, 10))
        )

        val bytes = frame.toBytes()
        val parsed = AckFrame.fromBytes(bytes)

        assertNotNull(parsed)
        assertEquals(10, parsed!!.largestAcked)
        assertEquals(1000, parsed.ackDelayMicros)
        assertEquals(1, parsed.ranges.size)
        assertEquals(AckRange(1, 10), parsed.ranges[0])
    }

    @Test
    fun `multiple ranges with gaps round-trip`() {
        // Received: 1-5, 8-10, 12
        val frame = AckFrame(
            largestAcked = 12,
            ackDelayMicros = 500,
            ranges = listOf(
                AckRange(12, 12),
                AckRange(8, 10),
                AckRange(1, 5)
            )
        )

        val bytes = frame.toBytes()
        val parsed = AckFrame.fromBytes(bytes)

        assertNotNull(parsed)
        assertEquals(12, parsed!!.largestAcked)
        assertEquals(3, parsed.ranges.size)

        // Check ranges are preserved
        assertTrue(parsed.contains(1))
        assertTrue(parsed.contains(5))
        assertFalse(parsed.contains(6))
        assertFalse(parsed.contains(7))
        assertTrue(parsed.contains(8))
        assertTrue(parsed.contains(10))
        assertFalse(parsed.contains(11))
        assertTrue(parsed.contains(12))
    }

    @Test
    fun `fromPacketNumbers creates single range for contiguous packets`() {
        val frame = AckFrame.fromPacketNumbers(listOf(1L, 2L, 3L, 4L, 5L))

        assertNotNull(frame)
        assertEquals(5, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size)
        assertEquals(AckRange(1, 5), frame.ranges[0])
    }

    @Test
    fun `fromPacketNumbers creates multiple ranges for gaps`() {
        // Packets: 1, 2, 3, 7, 8, 9, 15
        val frame = AckFrame.fromPacketNumbers(listOf(1L, 2L, 3L, 7L, 8L, 9L, 15L))

        assertNotNull(frame)
        assertEquals(15, frame!!.largestAcked)
        assertEquals(3, frame.ranges.size)

        // Ranges should be in descending order
        assertEquals(AckRange(15, 15), frame.ranges[0])
        assertEquals(AckRange(7, 9), frame.ranges[1])
        assertEquals(AckRange(1, 3), frame.ranges[2])
    }

    @Test
    fun `fromPacketNumbers handles unordered input`() {
        val frame = AckFrame.fromPacketNumbers(listOf(5L, 1L, 3L, 2L, 4L))

        assertNotNull(frame)
        assertEquals(5, frame!!.largestAcked)
        assertEquals(1, frame.ranges.size)
        assertEquals(AckRange(1, 5), frame.ranges[0])
    }

    @Test
    fun `fromPacketNumbers handles duplicate packet numbers`() {
        val frame = AckFrame.fromPacketNumbers(listOf(1L, 2L, 2L, 3L, 3L, 3L))

        assertNotNull(frame)
        // Should deduplicate and create single range
        assertEquals(3, frame!!.largestAcked)
    }

    @Test
    fun `fromPacketNumbers returns null for empty collection`() {
        assertNull(AckFrame.fromPacketNumbers(emptyList()))
    }

    @Test
    fun `fromPacketNumbers preserves ack delay`() {
        val frame = AckFrame.fromPacketNumbers(listOf(1L), ackDelayMicros = 12345)
        assertEquals(12345, frame!!.ackDelayMicros)
    }

    @Test
    fun `contains checks all ranges`() {
        val frame = AckFrame(
            largestAcked = 20,
            ackDelayMicros = 0,
            ranges = listOf(
                AckRange(20, 20),
                AckRange(10, 15),
                AckRange(1, 5)
            )
        )

        assertTrue(frame.contains(1))
        assertTrue(frame.contains(3))
        assertTrue(frame.contains(5))
        assertFalse(frame.contains(6))
        assertFalse(frame.contains(9))
        assertTrue(frame.contains(10))
        assertTrue(frame.contains(12))
        assertTrue(frame.contains(15))
        assertFalse(frame.contains(16))
        assertFalse(frame.contains(19))
        assertTrue(frame.contains(20))
        assertFalse(frame.contains(21))
    }

    @Test
    fun `fromBytes returns null for too short data`() {
        assertNull(AckFrame.fromBytes(ByteArray(6)))
    }

    @Test
    fun `fromBytes returns null for zero range count`() {
        val bytes = ByteArray(7)
        bytes[6] = 0 // range count = 0
        assertNull(AckFrame.fromBytes(bytes))
    }

    @Test
    fun `constructor rejects empty ranges`() {
        assertThrows<IllegalArgumentException> {
            AckFrame(10, 0, emptyList())
        }
    }

    @Test
    fun `constructor rejects first range not matching largestAcked`() {
        assertThrows<IllegalArgumentException> {
            AckFrame(10, 0, listOf(AckRange(1, 5)))
        }
    }

    @Test
    fun `constructor rejects negative largestAcked`() {
        assertThrows<IllegalArgumentException> {
            AckFrame(-1, 0, listOf(AckRange(1, 5)))
        }
    }

    @Test
    fun `constructor rejects negative ackDelay`() {
        assertThrows<IllegalArgumentException> {
            AckFrame(5, -1, listOf(AckRange(1, 5)))
        }
    }

    @Test
    fun `large ack delay round-trips`() {
        val frame = AckFrame(
            largestAcked = 1,
            ackDelayMicros = 65535, // Max unsigned 16-bit
            ranges = listOf(AckRange(1, 1))
        )

        val parsed = AckFrame.fromBytes(frame.toBytes())
        assertEquals(65535, parsed!!.ackDelayMicros)
    }

    @Test
    fun `large sequence numbers round-trip`() {
        val frame = AckFrame(
            largestAcked = 0xFFFFFFFFL, // Max unsigned 32-bit
            ackDelayMicros = 0,
            ranges = listOf(AckRange(0xFFFFFFFFL, 0xFFFFFFFFL))
        )

        val parsed = AckFrame.fromBytes(frame.toBytes())
        assertEquals(0xFFFFFFFFL, parsed!!.largestAcked)
    }
}
