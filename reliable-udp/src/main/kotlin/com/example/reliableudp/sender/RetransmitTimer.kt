package com.example.reliableudp.sender

/**
 * Manages retransmission timing using Probe Timeout (PTO).
 *
 * Based on QUIC RFC 9002 Section 6: Loss Detection.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9002.html#section-6">RFC 9002</a>
 */
interface RetransmitTimer {
    /**
     * Start the retransmit check loop.
     *
     * Periodically checks for packets that have exceeded PTO and triggers
     * retransmission via [onRetransmit] callback.
     */
    fun start()

    /**
     * Stop the retransmit loop.
     *
     * Cancels any pending retransmission checks.
     */
    fun stop()

    /**
     * Callback when a packet needs retransmission.
     *
     * Called when a packet has been in-flight longer than PTO but has
     * not exceeded max retries.
     */
    var onRetransmit: ((SentPacket) -> Unit)?

    /**
     * Callback when max retries exceeded.
     *
     * Called when a packet has been retransmitted MAX_RETRIES times
     * without acknowledgment.
     */
    var onMaxRetriesExceeded: ((SentPacket) -> Unit)?
}
