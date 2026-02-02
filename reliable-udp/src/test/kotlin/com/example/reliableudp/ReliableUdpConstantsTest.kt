package com.example.reliableudp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReliableUdpConstantsTest {

    @Test
    fun `header size is 11 bytes`() {
        assertEquals(11, ReliableUdpConstants.HEADER_SIZE)
    }

    @Test
    fun `max payload size fits within typical MTU`() {
        // MTU 1500 - IP header (20) - UDP header (8) = 1472
        // We use 1400 for safety margin
        assertTrue(ReliableUdpConstants.MAX_PAYLOAD_SIZE <= 1472)
        assertEquals(1400, ReliableUdpConstants.MAX_PAYLOAD_SIZE)
    }

    @Test
    fun `max packet size is header plus payload`() {
        assertEquals(
            ReliableUdpConstants.HEADER_SIZE + ReliableUdpConstants.MAX_PAYLOAD_SIZE,
            ReliableUdpConstants.MAX_PACKET_SIZE
        )
    }

    @Test
    fun `max message size is 64KB`() {
        assertEquals(64 * 1024, ReliableUdpConstants.MAX_MESSAGE_SIZE)
    }

    @Test
    fun `initial RTT is 333ms per RFC 9002`() {
        assertEquals(333_000_000L, ReliableUdpConstants.INITIAL_RTT_NS)
    }

    @Test
    fun `timer granularity is 1ms per RFC 9002`() {
        assertEquals(1_000_000L, ReliableUdpConstants.GRANULARITY_NS)
    }

    @Test
    fun `packet threshold is 3 per RFC 9002`() {
        assertEquals(3, ReliableUdpConstants.PACKET_THRESHOLD)
    }

    @Test
    fun `time threshold is 9 over 8 per RFC 9002`() {
        assertEquals(9, ReliableUdpConstants.TIME_THRESHOLD_NUM)
        assertEquals(8, ReliableUdpConstants.TIME_THRESHOLD_DEN)
    }

    @Test
    fun `max retries is reasonable`() {
        assertTrue(ReliableUdpConstants.MAX_RETRIES in 5..20)
    }

    @Test
    fun `fragment timeout is 30 seconds`() {
        assertEquals(30_000L, ReliableUdpConstants.FRAGMENT_TIMEOUT_MS)
    }

    @Test
    fun `max fragments per message fits in byte`() {
        assertTrue(ReliableUdpConstants.MAX_FRAGMENT_INDEX <= 255)
    }

    @Test
    fun `64KB can be fragmented within max fragment limit`() {
        val fragmentsNeeded = (ReliableUdpConstants.MAX_MESSAGE_SIZE + ReliableUdpConstants.MAX_PAYLOAD_SIZE - 1) /
                ReliableUdpConstants.MAX_PAYLOAD_SIZE
        assertTrue(
            fragmentsNeeded <= ReliableUdpConstants.MAX_FRAGMENT_INDEX,
            "64KB requires $fragmentsNeeded fragments, max is ${ReliableUdpConstants.MAX_FRAGMENT_INDEX}"
        )
    }
}
