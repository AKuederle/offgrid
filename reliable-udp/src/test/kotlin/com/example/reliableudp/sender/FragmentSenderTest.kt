package com.example.reliableudp.sender

import com.example.reliableudp.ReliableUdpConstants
import com.example.reliableudp.protocol.Header
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

class FragmentSenderTest {

    private lateinit var sender: FragmentSenderImpl
    private val testDestination = InetSocketAddress("127.0.0.1", 5000)
    private val seqNumCounter = AtomicLong(0)

    @BeforeEach
    fun setup() {
        sender = FragmentSenderImpl()
        seqNumCounter.set(0)
    }

    private fun nextSeqNum(): Long = seqNumCounter.getAndIncrement()

    @Test
    fun `needsFragmentation returns false for small payload`() {
        assertFalse(sender.needsFragmentation(100))
        assertFalse(sender.needsFragmentation(ReliableUdpConstants.MAX_PAYLOAD_SIZE))
    }

    @Test
    fun `needsFragmentation returns true for large payload`() {
        assertTrue(sender.needsFragmentation(ReliableUdpConstants.MAX_PAYLOAD_SIZE + 1))
        assertTrue(sender.needsFragmentation(10_000))
    }

    @Test
    fun `calculateFragmentCount returns 1 for small payload`() {
        assertEquals(1, sender.calculateFragmentCount(100))
        assertEquals(1, sender.calculateFragmentCount(ReliableUdpConstants.MAX_PAYLOAD_SIZE))
    }

    @Test
    fun `calculateFragmentCount returns correct count for large payload`() {
        assertEquals(2, sender.calculateFragmentCount(ReliableUdpConstants.MAX_PAYLOAD_SIZE + 1))
        assertEquals(2, sender.calculateFragmentCount(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 2))
        assertEquals(3, sender.calculateFragmentCount(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 2 + 1))
    }

    @Test
    fun `calculateFragmentCount for 64KB message`() {
        val expected = (ReliableUdpConstants.MAX_MESSAGE_SIZE + ReliableUdpConstants.MAX_PAYLOAD_SIZE - 1) /
                ReliableUdpConstants.MAX_PAYLOAD_SIZE
        assertEquals(expected, sender.calculateFragmentCount(ReliableUdpConstants.MAX_MESSAGE_SIZE))
    }

    @Test
    fun `fragment returns single packet for small message`() {
        val payload = ByteArray(100) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        assertEquals(1, packets.size)
        assertEquals(1, packets[0].fragmentIndex)
        assertEquals(1, packets[0].fragmentTotal)
    }

    @Test
    fun `fragment creates correct number of packets`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 3) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        assertEquals(3, packets.size)
    }

    @Test
    fun `fragment sets correct fragment indices`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 3) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        assertEquals(1, packets[0].fragmentIndex)
        assertEquals(2, packets[1].fragmentIndex)
        assertEquals(3, packets[2].fragmentIndex)

        packets.forEach { assertEquals(3, it.fragmentTotal) }
    }

    @Test
    fun `fragment assigns unique sequence numbers`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 3) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        val seqNums = packets.map { it.sequenceNumber }
        assertEquals(seqNums.size, seqNums.toSet().size) // All unique
    }

    @Test
    fun `fragment preserves message ID across all packets`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 3) { it.toByte() }
        val messageId = 42
        val packets = sender.fragment(messageId, payload, testDestination, ::nextSeqNum)

        packets.forEach { assertEquals(messageId, it.messageId) }
    }

    @Test
    fun `fragment preserves destination across all packets`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 3) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        packets.forEach { assertEquals(testDestination, it.destination) }
    }

    @Test
    fun `fragment payload can be parsed back via Header`() {
        val payload = ByteArray(100) { it.toByte() }
        val packets = sender.fragment(42, payload, testDestination, ::nextSeqNum)

        val packet = packets[0]
        val header = Header.fromBytes(packet.payload)

        assertNotNull(header)
        assertEquals(42, header!!.messageId)
        assertEquals(1, header.fragmentIndex)
        assertEquals(1, header.fragmentTotal)
    }

    @Test
    fun `fragment handles exact MTU size`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        assertEquals(1, packets.size)
    }

    @Test
    fun `fragment handles one byte over MTU`() {
        val payload = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE + 1) { it.toByte() }
        val packets = sender.fragment(1, payload, testDestination, ::nextSeqNum)

        assertEquals(2, packets.size)

        // First fragment should be full MTU
        val firstPayloadSize = packets[0].payload.size - ReliableUdpConstants.HEADER_SIZE
        assertEquals(ReliableUdpConstants.MAX_PAYLOAD_SIZE, firstPayloadSize)

        // Second fragment should have just 1 byte
        val secondPayloadSize = packets[1].payload.size - ReliableUdpConstants.HEADER_SIZE
        assertEquals(1, secondPayloadSize)
    }

    @Test
    fun `fragment handles empty payload`() {
        val packets = sender.fragment(1, ByteArray(0), testDestination, ::nextSeqNum)

        assertEquals(1, packets.size)
        assertEquals(1, packets[0].fragmentIndex)
        assertEquals(1, packets[0].fragmentTotal)
    }

    @Test
    fun `fragment rejects payload exceeding MAX_MESSAGE_SIZE`() {
        val oversizedPayload = ByteArray(ReliableUdpConstants.MAX_MESSAGE_SIZE + 1)

        assertThrows<IllegalArgumentException> {
            sender.fragment(1, oversizedPayload, testDestination, ::nextSeqNum)
        }
    }

    @Test
    fun `reassembled fragments match original payload`() {
        val original = ByteArray(ReliableUdpConstants.MAX_PAYLOAD_SIZE * 2 + 500) { it.toByte() }
        val packets = sender.fragment(1, original, testDestination, ::nextSeqNum)

        // Extract payloads (strip headers)
        val payloads = packets.map { packet ->
            packet.payload.copyOfRange(ReliableUdpConstants.HEADER_SIZE, packet.payload.size)
        }

        // Reassemble
        val reassembled = payloads.fold(ByteArray(0)) { acc, bytes -> acc + bytes }

        assertArrayEquals(original, reassembled)
    }
}
