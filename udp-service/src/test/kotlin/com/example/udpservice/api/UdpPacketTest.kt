package com.example.udpservice.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class UdpPacketTest {

    @Test
    fun `displayText returns UTF-8 string when data is valid UTF-8`() {
        val data = "Hello, World!".toByteArray(Charsets.UTF_8)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val packet = UdpPacket(data = data, sourceAddress = address, timestamp = 1000L)

        assertEquals("Hello, World!", packet.displayText)
    }

    @Test
    fun `displayText truncates UTF-8 string to 200 characters`() {
        val longString = "A".repeat(250)
        val data = longString.toByteArray(Charsets.UTF_8)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val packet = UdpPacket(data = data, sourceAddress = address, timestamp = 1000L)

        assertEquals(200, packet.displayText.length)
        assertEquals("A".repeat(200), packet.displayText)
    }

    @Test
    fun `displayText returns hex preview when data contains invalid UTF-8`() {
        // Invalid UTF-8 sequence: 0xFF is not valid in UTF-8
        val invalidData = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0xFF.toByte(), 0xFE.toByte())
        val address = InetSocketAddress("192.168.1.1", 5000)
        val packet = UdpPacket(data = invalidData, sourceAddress = address, timestamp = 1000L)

        // Should return hex representation
        assertTrue(packet.displayText.contains("48") || packet.displayText.lowercase().contains("48"))
        assertTrue(packet.displayText.lowercase().contains("ff") || packet.displayText.contains("FF"))
    }

    @Test
    fun `displayText hex preview shows first 32 bytes only`() {
        // Create 64 bytes of invalid UTF-8 data
        val invalidData = ByteArray(64) { 0xFF.toByte() }
        val address = InetSocketAddress("192.168.1.1", 5000)
        val packet = UdpPacket(data = invalidData, sourceAddress = address, timestamp = 1000L)

        // Hex preview should only contain first 32 bytes (64 hex characters + possible separators)
        // Each byte is 2 hex chars, so 32 bytes = 64 hex chars max (without separators)
        val hexContent = packet.displayText.replace(" ", "").replace(":", "")
        assertTrue(hexContent.length <= 64 + 10) // Allow some overhead for formatting
    }

    @Test
    fun `equals returns true for packets with same byte content`() {
        val data1 = byteArrayOf(1, 2, 3, 4, 5)
        val data2 = byteArrayOf(1, 2, 3, 4, 5)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val timestamp = 1000L

        val packet1 = UdpPacket(data = data1, sourceAddress = address, timestamp = timestamp)
        val packet2 = UdpPacket(data = data2, sourceAddress = address, timestamp = timestamp)

        assertEquals(packet1, packet2)
    }

    @Test
    fun `hashCode is same for packets with same byte content`() {
        val data1 = byteArrayOf(1, 2, 3, 4, 5)
        val data2 = byteArrayOf(1, 2, 3, 4, 5)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val timestamp = 1000L

        val packet1 = UdpPacket(data = data1, sourceAddress = address, timestamp = timestamp)
        val packet2 = UdpPacket(data = data2, sourceAddress = address, timestamp = timestamp)

        assertEquals(packet1.hashCode(), packet2.hashCode())
    }

    @Test
    fun `equals returns false for packets with different byte content`() {
        val data1 = byteArrayOf(1, 2, 3, 4, 5)
        val data2 = byteArrayOf(1, 2, 3, 4, 6)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val timestamp = 1000L

        val packet1 = UdpPacket(data = data1, sourceAddress = address, timestamp = timestamp)
        val packet2 = UdpPacket(data = data2, sourceAddress = address, timestamp = timestamp)

        assertNotEquals(packet1, packet2)
    }

    @Test
    fun `timestamp defaults to current time if not provided`() {
        val data = "Test".toByteArray(Charsets.UTF_8)
        val address = InetSocketAddress("192.168.1.1", 5000)

        val beforeCreation = System.currentTimeMillis()
        val packet = UdpPacket(data = data, sourceAddress = address)
        val afterCreation = System.currentTimeMillis()

        assertTrue(packet.timestamp >= beforeCreation)
        assertTrue(packet.timestamp <= afterCreation)
    }

    @Test
    fun `packet preserves source address`() {
        val data = "Test".toByteArray(Charsets.UTF_8)
        val address = InetSocketAddress("10.0.0.1", 8080)
        val packet = UdpPacket(data = data, sourceAddress = address, timestamp = 1000L)

        assertEquals(address, packet.sourceAddress)
    }

    @Test
    fun `packet preserves data content`() {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val address = InetSocketAddress("192.168.1.1", 5000)
        val packet = UdpPacket(data = data, sourceAddress = address, timestamp = 1000L)

        assertTrue(data.contentEquals(packet.data))
    }
}
