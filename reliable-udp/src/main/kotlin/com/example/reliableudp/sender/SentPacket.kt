package com.example.reliableudp.sender

import java.net.InetSocketAddress

/**
 * Represents a packet that has been sent and is awaiting acknowledgment.
 *
 * @property sequenceNumber Unique packet sequence number (strictly increasing)
 * @property messageId Identifier of the logical message this packet belongs to
 * @property fragmentIndex 1-based fragment position within the message
 * @property fragmentTotal Total number of fragments in the message
 * @property payload The serialized packet data (header + payload)
 * @property destination Target address for sending/retransmission
 * @property sentAt Timestamp when packet was (last) sent (System.nanoTime)
 * @property retransmitCount Number of times this packet has been retransmitted
 */
data class SentPacket(
    val sequenceNumber: Long,
    val messageId: Int,
    val fragmentIndex: Int,
    val fragmentTotal: Int,
    val payload: ByteArray,
    val destination: InetSocketAddress,
    val sentAt: Long = System.nanoTime(),
    val retransmitCount: Int = 0
) {
    /**
     * Create a copy for retransmission with new sequence number and timestamp.
     *
     * Per QUIC spec, retransmissions use new sequence numbers to avoid ACK ambiguity.
     *
     * @param newSeqNum New sequence number for the retransmission
     * @return New SentPacket with updated sequence number, timestamp, and retry count
     */
    fun retransmit(newSeqNum: Long): SentPacket = copy(
        sequenceNumber = newSeqNum,
        sentAt = System.nanoTime(),
        retransmitCount = retransmitCount + 1
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SentPacket) return false

        return sequenceNumber == other.sequenceNumber &&
                messageId == other.messageId &&
                fragmentIndex == other.fragmentIndex
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + messageId
        result = 31 * result + fragmentIndex
        return result
    }
}
