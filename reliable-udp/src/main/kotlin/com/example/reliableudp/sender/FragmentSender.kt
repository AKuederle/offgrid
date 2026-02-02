package com.example.reliableudp.sender

import java.net.InetSocketAddress

/**
 * Fragments large messages into MTU-sized packets.
 *
 * Messages larger than MAX_PAYLOAD_SIZE are split into multiple packets,
 * each with the same messageId but different fragmentIndex values.
 */
interface FragmentSender {
    /**
     * Fragment a message into packets ready for sending.
     *
     * @param messageId Unique ID for this message (for reassembly)
     * @param payload Complete message payload
     * @param destination Target address
     * @param sequenceNumberProvider Function to get next sequence number for each fragment
     * @return List of packets to send (1 if no fragmentation needed)
     * @throws IllegalArgumentException if payload exceeds MAX_MESSAGE_SIZE
     */
    fun fragment(
        messageId: Int,
        payload: ByteArray,
        destination: InetSocketAddress,
        sequenceNumberProvider: () -> Long
    ): List<SentPacket>

    /**
     * Check if a message needs fragmentation.
     *
     * @param payloadSize Size of the message payload in bytes
     * @return true if message exceeds MAX_PAYLOAD_SIZE
     */
    fun needsFragmentation(payloadSize: Int): Boolean

    /**
     * Calculate how many fragments a message will need.
     *
     * @param payloadSize Size of the message payload in bytes
     * @return Number of fragments required
     */
    fun calculateFragmentCount(payloadSize: Int): Int
}
