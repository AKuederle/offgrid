package com.example.udpservice.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.udpservice.send.DeliveryStatus

/**
 * Room entity representing an outbound message queued for delivery.
 *
 * Messages are persisted before transmission to survive app lifecycle events.
 *
 * @property id Auto-generated unique identifier
 * @property peerHost Target peer IP address
 * @property peerPort Target peer port
 * @property payload Message content to deliver (up to 64KB)
 * @property status Current delivery status
 * @property retryCount Number of retry attempts made
 * @property createdAt Timestamp when message was queued
 * @property lastAttemptAt Timestamp of last delivery attempt
 * @property deliveredAt Timestamp when ACK received (null if not delivered)
 */
@Entity(
    tableName = "outbound_messages",
    indices = [
        Index(value = ["peerHost", "peerPort", "status"]),
        Index(value = ["status", "lastAttemptAt"]),
        Index(value = ["createdAt"])
    ]
)
data class OutboundMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "peerHost")
    val peerHost: String,

    @ColumnInfo(name = "peerPort")
    val peerPort: Int,

    @ColumnInfo(name = "payload")
    val payload: ByteArray,

    @ColumnInfo(name = "status")
    val status: DeliveryStatus,

    @ColumnInfo(name = "retryCount")
    val retryCount: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "lastAttemptAt")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "deliveredAt")
    val deliveredAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OutboundMessageEntity
        return id == other.id &&
            peerHost == other.peerHost &&
            peerPort == other.peerPort &&
            payload.contentEquals(other.payload) &&
            status == other.status &&
            retryCount == other.retryCount &&
            createdAt == other.createdAt &&
            lastAttemptAt == other.lastAttemptAt &&
            deliveredAt == other.deliveredAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + peerHost.hashCode()
        result = 31 * result + peerPort
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + retryCount
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastAttemptAt?.hashCode() ?: 0)
        result = 31 * result + (deliveredAt?.hashCode() ?: 0)
        return result
    }
}
