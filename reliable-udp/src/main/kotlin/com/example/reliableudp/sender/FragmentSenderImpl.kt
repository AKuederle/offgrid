package com.example.reliableudp.sender

import com.example.reliableudp.ReliableUdpConstants
import com.example.reliableudp.protocol.Header
import com.example.reliableudp.protocol.PacketType
import java.net.InetSocketAddress

/**
 * Implementation of FragmentSender that splits messages at MTU boundary.
 *
 * Fragmentation algorithm:
 * 1. Calculate number of fragments needed based on MAX_PAYLOAD_SIZE
 * 2. Split payload into chunks
 * 3. Create SentPacket for each chunk with same messageId, sequential fragmentIndex
 * 4. Each fragment gets its own sequence number for independent ACK/retransmit
 */
class FragmentSenderImpl : FragmentSender {

    override fun fragment(
        messageId: Int,
        payload: ByteArray,
        destination: InetSocketAddress,
        sequenceNumberProvider: () -> Long
    ): List<SentPacket> {
        require(payload.size <= ReliableUdpConstants.MAX_MESSAGE_SIZE) {
            "Payload size ${payload.size} exceeds maximum ${ReliableUdpConstants.MAX_MESSAGE_SIZE}"
        }

        if (payload.isEmpty()) {
            // Single empty packet
            return listOf(createPacket(messageId, ByteArray(0), 1, 1, destination, sequenceNumberProvider()))
        }

        val fragmentTotal = calculateFragmentCount(payload.size)
        val packets = mutableListOf<SentPacket>()

        var offset = 0
        var fragmentIndex = 1

        while (offset < payload.size) {
            val chunkSize = minOf(ReliableUdpConstants.MAX_PAYLOAD_SIZE, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + chunkSize)

            packets.add(
                createPacket(
                    messageId = messageId,
                    fragmentPayload = chunk,
                    fragmentIndex = fragmentIndex,
                    fragmentTotal = fragmentTotal,
                    destination = destination,
                    sequenceNumber = sequenceNumberProvider()
                )
            )

            offset += chunkSize
            fragmentIndex++
        }

        return packets
    }

    private fun createPacket(
        messageId: Int,
        fragmentPayload: ByteArray,
        fragmentIndex: Int,
        fragmentTotal: Int,
        destination: InetSocketAddress,
        sequenceNumber: Long
    ): SentPacket {
        // Create header
        val header = Header(
            type = PacketType.DATA,
            messageId = messageId,
            sequenceNumber = sequenceNumber,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal
        )

        // Serialize header + payload
        val headerBytes = header.toBytes()
        val packetData = headerBytes + fragmentPayload

        return SentPacket(
            sequenceNumber = sequenceNumber,
            messageId = messageId,
            fragmentIndex = fragmentIndex,
            fragmentTotal = fragmentTotal,
            payload = packetData,
            destination = destination
        )
    }

    override fun needsFragmentation(payloadSize: Int): Boolean {
        return payloadSize > ReliableUdpConstants.MAX_PAYLOAD_SIZE
    }

    override fun calculateFragmentCount(payloadSize: Int): Int {
        if (payloadSize == 0) return 1
        return (payloadSize + ReliableUdpConstants.MAX_PAYLOAD_SIZE - 1) / ReliableUdpConstants.MAX_PAYLOAD_SIZE
    }
}
