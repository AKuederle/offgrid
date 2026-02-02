package com.example.reliableudp.rtt

/**
 * RTT (Round-Trip Time) estimator for calculating Probe Timeout (PTO).
 *
 * Based on RFC 9002 Section 5: RTT Estimation.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9002.html#section-5">RFC 9002</a>
 */
interface RttEstimator {
    /**
     * Update RTT estimate with a new sample.
     *
     * @param rttNanos Measured round-trip time in nanoseconds
     * @param ackDelayNanos ACK delay reported by receiver in nanoseconds
     */
    fun update(rttNanos: Long, ackDelayNanos: Long)

    /**
     * Get current Probe Timeout (PTO) value.
     *
     * PTO = smoothedRtt + max(4 * rttVar, granularity) + maxAckDelay
     *
     * @param maxAckDelayNanos Maximum ACK delay for the path (default 0)
     * @return PTO in nanoseconds
     */
    fun getPto(maxAckDelayNanos: Long = 0): Long

    /**
     * Current smoothed RTT estimate in nanoseconds.
     *
     * Computed as EWMA: smoothedRtt = 7/8 * smoothedRtt + 1/8 * sample
     */
    val smoothedRtt: Long

    /**
     * RTT variance in nanoseconds.
     *
     * Computed as: rttVar = 3/4 * rttVar + 1/4 * |smoothedRtt - sample|
     */
    val rttVariance: Long

    /**
     * Minimum observed RTT in nanoseconds.
     *
     * Used to filter ACK delay from RTT samples.
     */
    val minRtt: Long
}
