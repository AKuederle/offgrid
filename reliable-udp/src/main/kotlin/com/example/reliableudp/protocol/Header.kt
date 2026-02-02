package com.example.reliableudp.protocol

import com.example.reliableudp.ReliableUdpConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reliable UDP packet header.
 *
 * Wire format (11 bytes, big-endian):
 * ```
 * ┌──────────┬──────────┬───────────┬───────────┬──────────────┐
 * │ Type (1) │ MsgID(4) │ SeqNum(4) │ FragIdx(1)│ FragTotal(1) │
 * └──────────┴──────────┴───────────┴───────────┴──────────────┘
 * ```
 *
 * @property type Packet type (DATA, ACK, PING)
 * @property messageId Unique identifier for logical message (for fragment reassembly)
 * @property sequenceNumber Strictly increasing packet number (never reused)
 * @property fragmentIndex 1-based fragment position (1 for unfragmented messages)
 * @property fragmentTotal Total number of fragments (1 for unfragmented messages)
 */
data class Header(
    val type: PacketType,
    val messageId: Int,
    val sequenceNumber: Long,
    val fragmentIndex: Int,
    val fragmentTotal: Int
) {
    init {
        require(fragmentIndex in 1..ReliableUdpConstants.MAX_FRAGMENT_INDEX) {
            "fragmentIndex must be 1-${ReliableUdpConstants.MAX_FRAGMENT_INDEX}, got $fragmentIndex"
        }
        require(fragmentTotal in 1..ReliableUdpConstants.MAX_FRAGMENT_INDEX) {
            "fragmentTotal must be 1-${ReliableUdpConstants.MAX_FRAGMENT_INDEX}, got $fragmentTotal"
        }
        require(fragmentIndex <= fragmentTotal) {
            "fragmentIndex ($fragmentIndex) cannot exceed fragmentTotal ($fragmentTotal)"
        }
        require(sequenceNumber >= 0) {
            "sequenceNumber must be non-negative, got $sequenceNumber"
        }
    }

    /**
     * Serialize header to wire format bytes.
     *
     * @return 11-byte array in big-endian format
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(ReliableUdpConstants.HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)

        buffer.put(type.value)
        buffer.putInt(messageId)
        buffer.putInt(sequenceNumber.toInt()) // Using lower 32 bits for wire format
        buffer.put(fragmentIndex.toByte())
        buffer.put(fragmentTotal.toByte())

        return buffer.array()
    }

    companion object {
        /**
         * Parse header from wire format bytes.
         *
         * @param data Byte array containing at least HEADER_SIZE bytes
         * @param offset Starting position in array (default 0)
         * @return Parsed header, or null if invalid
         */
        fun fromBytes(data: ByteArray, offset: Int = 0): Header? {
            if (data.size - offset < ReliableUdpConstants.HEADER_SIZE) {
                return null
            }

            val buffer = ByteBuffer.wrap(data, offset, ReliableUdpConstants.HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)

            val typeByte = buffer.get()
            val type = PacketType.fromByte(typeByte) ?: return null

            val messageId = buffer.int
            val sequenceNumber = buffer.int.toLong() and 0xFFFFFFFFL // Unsigned conversion
            val fragmentIndex = buffer.get().toInt() and 0xFF
            val fragmentTotal = buffer.get().toInt() and 0xFF

            // Validate fragment indices
            if (fragmentIndex < 1 || fragmentTotal < 1 || fragmentIndex > fragmentTotal) {
                return null
            }

            return Header(
                type = type,
                messageId = messageId,
                sequenceNumber = sequenceNumber,
                fragmentIndex = fragmentIndex,
                fragmentTotal = fragmentTotal
            )
        }
    }
}
