package com.example.reliableudp.protocol

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class PacketTypeTest {

    @Test
    fun `DATA has value 0x01`() {
        assertEquals(0x01.toByte(), PacketType.DATA.value)
    }

    @Test
    fun `ACK has value 0x02`() {
        assertEquals(0x02.toByte(), PacketType.ACK.value)
    }

    @Test
    fun `PING has value 0x03`() {
        assertEquals(0x03.toByte(), PacketType.PING.value)
    }

    @ParameterizedTest
    @EnumSource(PacketType::class)
    fun `fromByte round-trips all packet types`(type: PacketType) {
        assertEquals(type, PacketType.fromByte(type.value))
    }

    @Test
    fun `fromByte returns null for unknown type 0x00`() {
        assertNull(PacketType.fromByte(0x00))
    }

    @Test
    fun `fromByte returns null for unknown type 0x04`() {
        assertNull(PacketType.fromByte(0x04))
    }

    @ParameterizedTest
    @ValueSource(bytes = [0x10, 0x20, 0x7F, -1, -128])
    fun `fromByte returns null for invalid bytes`(value: Byte) {
        assertNull(PacketType.fromByte(value))
    }

    @Test
    fun `all packet types have unique values`() {
        val values = PacketType.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
    }
}
