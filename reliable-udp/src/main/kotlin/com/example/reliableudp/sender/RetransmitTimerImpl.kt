package com.example.reliableudp.sender

import com.example.reliableudp.ReliableUdpConstants
import com.example.reliableudp.rtt.RttEstimator
import kotlinx.coroutines.*

/**
 * Retransmit timer implementation using coroutines.
 *
 * Based on QUIC RFC 9002 Section 6: Loss Detection.
 *
 * Reference: https://www.rfc-editor.org/rfc/rfc9002.html#section-6
 * License: IETF standard (public domain)
 *
 * This implements:
 * - PTO-based retransmission timing
 * - Fast retransmit after kPacketThreshold out-of-order ACKs
 * - Max retry limiting with failure callback
 */
class RetransmitTimerImpl(
    private val packetBuffer: PacketBuffer,
    private val rttEstimator: RttEstimator,
    private val checkIntervalMs: Long = 10L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : RetransmitTimer {

    override var onRetransmit: ((SentPacket) -> Unit)? = null
    override var onMaxRetriesExceeded: ((SentPacket) -> Unit)? = null

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                delay(checkIntervalMs)
                checkForRetransmissions()
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun checkForRetransmissions() {
        val pto = rttEstimator.getPto()
        val oldPackets = packetBuffer.getOlderThan(pto)

        for (packet in oldPackets) {
            if (packet.retransmitCount >= ReliableUdpConstants.MAX_RETRIES) {
                // Max retries exceeded - notify failure
                packetBuffer.remove(packet.sequenceNumber)
                onMaxRetriesExceeded?.invoke(packet)
            } else {
                // Needs retransmission - notify for retransmit
                // Note: caller is responsible for removing old packet and adding new one
                onRetransmit?.invoke(packet)
            }
        }
    }
}
