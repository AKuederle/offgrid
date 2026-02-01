package com.example.udpservice.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a stored UDP packet.
 *
 * @property id Auto-generated unique identifier
 * @property appId Application identifier extracted from packet prefix (indexed for filtering)
 * @property data Raw packet payload without the appId prefix (up to 64KB)
 * @property sourceIp Source IP address as string (e.g., "192.168.1.100")
 * @property sourcePort Source UDP port (1-65535)
 * @property timestamp Reception time in milliseconds since epoch
 */
@Entity(
    tableName = "packets",
    indices = [
        Index(value = ["appId"]),
        Index(value = ["timestamp"], orders = [Index.Order.DESC]),
        Index(value = ["appId", "timestamp"], orders = [Index.Order.ASC, Index.Order.DESC])
    ]
)
data class PacketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "appId")
    val appId: String,

    @ColumnInfo(name = "data")
    val data: ByteArray,

    @ColumnInfo(name = "sourceIp")
    val sourceIp: String,

    @ColumnInfo(name = "sourcePort")
    val sourcePort: Int,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PacketEntity
        return id == other.id &&
            appId == other.appId &&
            data.contentEquals(other.data) &&
            sourceIp == other.sourceIp &&
            sourcePort == other.sourcePort &&
            timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + appId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sourceIp.hashCode()
        result = 31 * result + sourcePort
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
