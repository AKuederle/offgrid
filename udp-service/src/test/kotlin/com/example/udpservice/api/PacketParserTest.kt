package com.example.udpservice.api

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PacketParserTest {

    @Test
    fun `parse returns null for empty data`() {
        val result = PacketParser.parse(ByteArray(0))
        assertNull(result)
    }

    @Test
    fun `parse returns null when length byte is zero`() {
        val data = byteArrayOf(0x00, 0x41, 0x42) // length=0
        val result = PacketParser.parse(data)
        assertNull(result)
    }

    @Test
    fun `parse returns null when data too short for declared length`() {
        val data = byteArrayOf(0x05, 0x41, 0x42) // length=5 but only 2 bytes of appId
        val result = PacketParser.parse(data)
        assertNull(result)
    }

    @Test
    fun `parse extracts appId correctly`() {
        // length=4, appId="test", payload="hello"
        val data = byteArrayOf(0x04) + "test".toByteArray() + "hello".toByteArray()
        val result = PacketParser.parse(data)

        assertNotNull(result)
        assertEquals("test", result!!.appId)
        assertArrayEquals("hello".toByteArray(), result.payload)
    }

    @Test
    fun `parse handles appId with no payload`() {
        // length=3, appId="abc", no payload
        val data = byteArrayOf(0x03) + "abc".toByteArray()
        val result = PacketParser.parse(data)

        assertNotNull(result)
        assertEquals("abc", result!!.appId)
        assertEquals(0, result.payload.size)
    }

    @Test
    fun `parse handles single byte appId`() {
        val data = byteArrayOf(0x01, 0x41, 0x42, 0x43) // length=1, appId="A", payload="BC"
        val result = PacketParser.parse(data)

        assertNotNull(result)
        assertEquals("A", result!!.appId)
        assertArrayEquals("BC".toByteArray(), result.payload)
    }

    @Test
    fun `parse handles maximum length appId`() {
        val appId = "x".repeat(255)
        val payload = "payload".toByteArray()
        val data = byteArrayOf(0xFF.toByte()) + appId.toByteArray() + payload

        val result = PacketParser.parse(data)

        assertNotNull(result)
        assertEquals(appId, result!!.appId)
        assertArrayEquals(payload, result.payload)
    }

    @Test
    fun `encode creates correct format`() {
        val result = PacketParser.encode("test", "hello".toByteArray())

        assertNotNull(result)
        assertEquals(0x04, result!![0].toInt()) // length
        assertEquals("test", String(result.sliceArray(1..4)))
        assertArrayEquals("hello".toByteArray(), result.sliceArray(5 until result.size))
    }

    @Test
    fun `encode returns null for empty appId`() {
        val result = PacketParser.encode("", "payload".toByteArray())
        assertNull(result)
    }

    @Test
    fun `encode returns null for appId over 255 bytes`() {
        val longAppId = "x".repeat(256)
        val result = PacketParser.encode(longAppId, "payload".toByteArray())
        assertNull(result)
    }

    @Test
    fun `encode and parse are inverse operations`() {
        val appId = "myapp"
        val payload = "test payload data".toByteArray()

        val encoded = PacketParser.encode(appId, payload)
        assertNotNull(encoded)

        val parsed = PacketParser.parse(encoded!!)
        assertNotNull(parsed)
        assertEquals(appId, parsed!!.appId)
        assertArrayEquals(payload, parsed.payload)
    }
}
