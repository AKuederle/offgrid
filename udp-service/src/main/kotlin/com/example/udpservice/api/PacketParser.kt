package com.example.udpservice.api

/**
 * Result of parsing a UDP packet's appId prefix.
 *
 * @property appId The extracted application identifier
 * @property payload The remaining packet data after the prefix
 */
data class ParsedPacket(
    val appId: String,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ParsedPacket
        return appId == other.appId && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = appId.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Utility for parsing the appId prefix from UDP packet data.
 *
 * Packet format:
 * - Byte 0: Length of appId (1-255)
 * - Bytes 1..length: appId as UTF-8 string
 * - Remaining bytes: Payload data
 */
object PacketParser {

    /**
     * Parse the appId prefix from raw packet data.
     *
     * @param data Raw packet data with length-prefixed appId
     * @return ParsedPacket if valid, null if invalid format
     */
    fun parse(data: ByteArray): ParsedPacket? {
        // Need at least 1 byte for length
        if (data.isEmpty()) return null

        val appIdLength = data[0].toInt() and 0xFF

        // Validate length
        if (appIdLength == 0) return null
        if (data.size < 1 + appIdLength) return null

        // Extract appId
        val appIdBytes = data.sliceArray(1 until 1 + appIdLength)
        val appId = try {
            String(appIdBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }

        // Extract remaining payload
        val payload = if (data.size > 1 + appIdLength) {
            data.sliceArray(1 + appIdLength until data.size)
        } else {
            ByteArray(0)
        }

        return ParsedPacket(appId, payload)
    }

    /**
     * Encode an appId and payload into the wire format.
     * Useful for testing and sending packets.
     *
     * @param appId Application identifier (max 255 bytes UTF-8)
     * @param payload The packet payload
     * @return Encoded packet data, or null if appId is too long
     */
    fun encode(appId: String, payload: ByteArray): ByteArray? {
        val appIdBytes = appId.toByteArray(Charsets.UTF_8)
        if (appIdBytes.isEmpty() || appIdBytes.size > 255) return null

        return byteArrayOf(appIdBytes.size.toByte()) + appIdBytes + payload
    }
}
