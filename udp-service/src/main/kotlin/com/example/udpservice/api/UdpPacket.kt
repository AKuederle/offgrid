package com.example.udpservice.api

import com.example.udpservice.persistence.PacketEntity
import java.net.InetSocketAddress
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Represents a received UDP packet with metadata.
 *
 * @property data The raw packet payload as a byte array
 * @property sourceAddress The source address and port of the sender
 * @property timestamp The time the packet was received (milliseconds since epoch)
 */
data class UdpPacket(
    val data: ByteArray,
    val sourceAddress: InetSocketAddress,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Human-readable representation of the packet data.
     * Returns UTF-8 decoded string (truncated to 200 chars) if valid UTF-8,
     * otherwise returns a hex preview of the first 32 bytes.
     */
    val displayText: String
        get() = tryDecodeUtf8() ?: toHexPreview()

    /**
     * Attempts to decode the data as UTF-8.
     * @return The decoded string truncated to 200 characters, or null if decoding fails
     */
    private fun tryDecodeUtf8(): String? {
        return try {
            val decoder = Charsets.UTF_8.newDecoder().apply {
                onMalformedInput(CodingErrorAction.REPORT)
                onUnmappableCharacter(CodingErrorAction.REPORT)
            }
            val decoded = decoder.decode(java.nio.ByteBuffer.wrap(data)).toString()
            if (decoded.length > 200) {
                decoded.substring(0, 200)
            } else {
                decoded
            }
        } catch (e: CharacterCodingException) {
            null
        }
    }

    /**
     * Converts the first 32 bytes of data to uppercase hex with space separators.
     * @return Hex string like "48 65 6C 6C 6F"
     */
    private fun toHexPreview(): String {
        return data.take(32)
            .joinToString(" ") { byte ->
                "%02X".format(byte)
            }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UdpPacket

        if (!data.contentEquals(other.data)) return false
        if (sourceAddress != other.sourceAddress) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + sourceAddress.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    /**
     * Convert this UdpPacket to a PacketEntity for database persistence.
     * Parses the length-prefixed appId from the packet data.
     *
     * @return PacketEntity if appId prefix is valid, null otherwise
     */
    fun toEntity(): PacketEntity? {
        val parsed = PacketParser.parse(this.data) ?: return null
        return PacketEntity(
            appId = parsed.appId,
            data = parsed.payload,
            sourceIp = this.sourceAddress.hostString,
            sourcePort = this.sourceAddress.port,
            timestamp = this.timestamp
        )
    }

    /**
     * Convert this UdpPacket to a PacketEntity with a pre-parsed appId and payload.
     * Use this when you've already validated the appId against registered apps.
     *
     * @param appId The validated application identifier
     * @param payload The packet payload (without appId prefix)
     * @return PacketEntity for persistence
     */
    fun toEntity(appId: String, payload: ByteArray): PacketEntity = PacketEntity(
        appId = appId,
        data = payload,
        sourceIp = this.sourceAddress.hostString,
        sourcePort = this.sourceAddress.port,
        timestamp = this.timestamp
    )
}
