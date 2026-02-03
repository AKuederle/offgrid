package com.example.reliableudp.protocol

/**
 * Packet type identifier for reliable UDP protocol.
 *
 * Wire format: Single byte at offset 0 of packet header.
 */
enum class PacketType(val value: Byte) {
    /** Data packet carrying payload, requires acknowledgment */
    DATA(0x01),

    /** Acknowledgment packet with SACK ranges */
    ACK(0x02),

    /** Keep-alive / RTT measurement packet */
    PING(0x03),

    /** Presence announcement for peer discovery */
    PRESENCE(0x04);

    companion object {
        /**
         * Parse packet type from wire format byte.
         *
         * @param value The byte value from packet header
         * @return PacketType if valid, null if unknown type
         */
        fun fromByte(value: Byte): PacketType? = entries.find { it.value == value }
    }
}
