package com.example.reliableudp

import java.net.InetSocketAddress

/**
 * A complete message received from a remote sender.
 *
 * Messages are emitted only after all fragments are received and reassembled.
 * Duplicates are filtered out by the deduplication cache.
 *
 * @property payload The complete message data
 * @property source The sender's address and port
 * @property receivedAt Timestamp when the message was fully received (milliseconds since epoch)
 */
data class ReceivedMessage(
    val payload: ByteArray,
    val source: InetSocketAddress,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceivedMessage) return false

        return payload.contentEquals(other.payload) &&
                source == other.source &&
                receivedAt == other.receivedAt
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + receivedAt.hashCode()
        return result
    }

    override fun toString(): String {
        return "ReceivedMessage(payload=${payload.size} bytes, source=$source, receivedAt=$receivedAt)"
    }
}
