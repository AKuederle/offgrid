package com.example.udpservice.send

import java.net.InetSocketAddress

/**
 * Domain model for an outbound message queued for delivery.
 *
 * This is a read-only view of a message's current state,
 * used for observation and display purposes.
 *
 * @property id Unique message identifier
 * @property peer Target peer address
 * @property payload Message content
 * @property status Current delivery status
 * @property retryCount Number of retry attempts made
 * @property createdAt Timestamp when message was queued (epoch millis)
 * @property lastAttemptAt Timestamp of last delivery attempt (null if never attempted)
 * @property deliveredAt Timestamp when ACK received (null if not delivered)
 */
data class OutboundMessage(
    val id: Long,
    val peer: InetSocketAddress,
    val payload: ByteArray,
    val status: DeliveryStatus,
    val retryCount: Int,
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val deliveredAt: Long?
) {
    /**
     * Check if this message is in a terminal state.
     */
    val isTerminal: Boolean
        get() = status == DeliveryStatus.DELIVERED

    /**
     * Check if this message is awaiting delivery.
     */
    val isPending: Boolean
        get() = status in listOf(
            DeliveryStatus.PENDING,
            DeliveryStatus.SENDING,
            DeliveryStatus.RETRYING,
            DeliveryStatus.WAITING
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboundMessage) return false

        return id == other.id &&
                peer == other.peer &&
                payload.contentEquals(other.payload) &&
                status == other.status &&
                retryCount == other.retryCount &&
                createdAt == other.createdAt &&
                lastAttemptAt == other.lastAttemptAt &&
                deliveredAt == other.deliveredAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + peer.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + retryCount
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastAttemptAt?.hashCode() ?: 0)
        result = 31 * result + (deliveredAt?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "OutboundMessage(id=$id, peer=$peer, payload=${payload.size} bytes, status=$status, retryCount=$retryCount)"
    }
}
