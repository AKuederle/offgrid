package com.example.reliableudp.rtt

import com.example.reliableudp.ReliableUdpConstants
import kotlin.math.abs

/**
 * RTT estimator implementation using EWMA per RFC 9002.
 *
 * Reference: https://www.rfc-editor.org/rfc/rfc9002.html#section-5
 * License: IETF standard (public domain)
 *
 * This implements:
 * - Smoothed RTT: EWMA with 7/8 weight on existing estimate
 * - RTT Variance: EWMA with 3/4 weight on existing variance
 * - Min RTT: Minimum observed RTT (for ACK delay filtering)
 * - PTO calculation: smoothedRtt + max(4 * rttVar, granularity) + maxAckDelay
 */
class RttEstimatorImpl : RttEstimator {

    @Volatile
    private var _smoothedRtt: Long = ReliableUdpConstants.INITIAL_RTT_NS

    @Volatile
    private var _rttVariance: Long = ReliableUdpConstants.INITIAL_RTT_NS / 2

    @Volatile
    private var _minRtt: Long = Long.MAX_VALUE

    @Volatile
    private var firstSample = true

    override val smoothedRtt: Long get() = _smoothedRtt
    override val rttVariance: Long get() = _rttVariance
    override val minRtt: Long get() = if (_minRtt == Long.MAX_VALUE) ReliableUdpConstants.INITIAL_RTT_NS else _minRtt

    @Synchronized
    override fun update(rttNanos: Long, ackDelayNanos: Long) {
        require(rttNanos > 0) { "RTT sample must be positive" }

        // Update minimum RTT (always, regardless of ACK delay)
        _minRtt = minOf(_minRtt, rttNanos)

        // Adjust for ACK delay only if sample is above min_rtt + ack_delay
        // This prevents artificially reducing RTT estimates
        val adjustedRtt = if (rttNanos - _minRtt >= ackDelayNanos && ackDelayNanos > 0) {
            rttNanos - ackDelayNanos
        } else {
            rttNanos
        }

        if (firstSample) {
            // First sample: initialize directly
            _smoothedRtt = adjustedRtt
            _rttVariance = adjustedRtt / 2
            firstSample = false
        } else {
            // Subsequent samples: use EWMA
            // rttVar = 3/4 * rttVar + 1/4 * |smoothedRtt - sample|
            val rttVarSample = abs(_smoothedRtt - adjustedRtt)
            _rttVariance = (3 * _rttVariance + rttVarSample) / 4

            // smoothedRtt = 7/8 * smoothedRtt + 1/8 * sample
            _smoothedRtt = (7 * _smoothedRtt + adjustedRtt) / 8
        }
    }

    override fun getPto(maxAckDelayNanos: Long): Long {
        // PTO = smoothedRtt + max(4 * rttVar, granularity) + maxAckDelay
        val variance = maxOf(4 * _rttVariance, ReliableUdpConstants.GRANULARITY_NS)
        return _smoothedRtt + variance + maxAckDelayNanos
    }

    /**
     * Calculate backed-off PTO after consecutive timeouts.
     *
     * @param consecutiveTimeouts Number of consecutive PTO expirations
     * @return PTO with exponential backoff (capped at 2^MAX_PTO_BACKOFF)
     */
    fun getBackedOffPto(consecutiveTimeouts: Int, maxAckDelayNanos: Long = 0): Long {
        val basePto = getPto(maxAckDelayNanos)
        val backoffMultiplier = 1L shl minOf(consecutiveTimeouts, ReliableUdpConstants.MAX_PTO_BACKOFF)
        return basePto * backoffMultiplier
    }
}
