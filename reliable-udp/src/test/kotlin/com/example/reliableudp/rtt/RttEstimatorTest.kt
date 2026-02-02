package com.example.reliableudp.rtt

import com.example.reliableudp.ReliableUdpConstants
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows

class RttEstimatorTest {

    private lateinit var estimator: RttEstimatorImpl

    @BeforeEach
    fun setup() {
        estimator = RttEstimatorImpl()
    }

    @Test
    fun `initial smoothedRtt is INITIAL_RTT`() {
        assertEquals(ReliableUdpConstants.INITIAL_RTT_NS, estimator.smoothedRtt)
    }

    @Test
    fun `initial rttVariance is half of INITIAL_RTT`() {
        assertEquals(ReliableUdpConstants.INITIAL_RTT_NS / 2, estimator.rttVariance)
    }

    @Test
    fun `initial minRtt returns INITIAL_RTT when no samples`() {
        assertEquals(ReliableUdpConstants.INITIAL_RTT_NS, estimator.minRtt)
    }

    @Test
    fun `first sample sets smoothedRtt directly`() {
        val sampleNanos = 100_000_000L // 100ms
        estimator.update(sampleNanos, 0)

        assertEquals(sampleNanos, estimator.smoothedRtt)
    }

    @Test
    fun `first sample sets rttVariance to half of sample`() {
        val sampleNanos = 100_000_000L // 100ms
        estimator.update(sampleNanos, 0)

        assertEquals(sampleNanos / 2, estimator.rttVariance)
    }

    @Test
    fun `minRtt is updated with each sample`() {
        estimator.update(100_000_000L, 0) // 100ms
        assertEquals(100_000_000L, estimator.minRtt)

        estimator.update(50_000_000L, 0) // 50ms - lower
        assertEquals(50_000_000L, estimator.minRtt)

        estimator.update(200_000_000L, 0) // 200ms - higher
        assertEquals(50_000_000L, estimator.minRtt) // Still 50ms
    }

    @Test
    fun `EWMA smooths subsequent samples`() {
        estimator.update(100_000_000L, 0) // First: 100ms
        assertEquals(100_000_000L, estimator.smoothedRtt)

        estimator.update(200_000_000L, 0) // Second: 200ms
        // smoothedRtt = 7/8 * 100 + 1/8 * 200 = 87.5 + 25 = 112.5ms
        assertEquals((7 * 100_000_000L + 200_000_000L) / 8, estimator.smoothedRtt)
    }

    @Test
    fun `ackDelay is subtracted when sample is above minRtt plus ackDelay`() {
        estimator.update(100_000_000L, 0) // First sample: 100ms, minRtt = 100ms
        estimator.update(150_000_000L, 30_000_000L) // 150ms with 30ms ack delay

        // Since 150 - 100 = 50ms >= 30ms ack delay, adjust to 120ms
        // smoothedRtt = 7/8 * 100 + 1/8 * 120 = 87.5 + 15 = 102.5ms
        assertEquals((7 * 100_000_000L + 120_000_000L) / 8, estimator.smoothedRtt)
    }

    @Test
    fun `ackDelay is not subtracted when it would reduce below minRtt`() {
        estimator.update(100_000_000L, 0) // First sample: 100ms, minRtt = 100ms
        estimator.update(110_000_000L, 50_000_000L) // 110ms with 50ms ack delay

        // Since 110 - 100 = 10ms < 50ms ack delay, don't adjust (use raw 110ms)
        // smoothedRtt = 7/8 * 100 + 1/8 * 110 = 87.5 + 13.75 ≈ 101.25ms
        assertEquals((7 * 100_000_000L + 110_000_000L) / 8, estimator.smoothedRtt)
    }

    @Test
    fun `getPto calculates correctly`() {
        estimator.update(100_000_000L, 0) // 100ms RTT

        // PTO = smoothedRtt + max(4 * rttVar, granularity)
        // rttVar = 100ms / 2 = 50ms
        // PTO = 100ms + max(4 * 50ms, 1ms) = 100ms + 200ms = 300ms
        val expectedPto = 100_000_000L + 4 * 50_000_000L
        assertEquals(expectedPto, estimator.getPto())
    }

    @Test
    fun `getPto uses granularity when variance is very small`() {
        // After many samples converging, variance becomes small
        estimator.update(10_000_000L, 0) // 10ms
        repeat(100) {
            estimator.update(10_000_000L, 0) // Consistent 10ms
        }

        // Variance should be very small
        // PTO should use granularity (1ms) as minimum
        val pto = estimator.getPto()
        assertTrue(pto >= estimator.smoothedRtt + ReliableUdpConstants.GRANULARITY_NS)
    }

    @Test
    fun `getPto includes maxAckDelay`() {
        estimator.update(100_000_000L, 0)
        val maxAckDelay = 25_000_000L // 25ms

        val ptoWithoutDelay = estimator.getPto()
        val ptoWithDelay = estimator.getPto(maxAckDelay)

        assertEquals(maxAckDelay, ptoWithDelay - ptoWithoutDelay)
    }

    @Test
    fun `getBackedOffPto applies exponential backoff`() {
        estimator.update(100_000_000L, 0)
        val basePto = estimator.getPto()

        assertEquals(basePto * 1, (estimator as RttEstimatorImpl).getBackedOffPto(0))
        assertEquals(basePto * 2, (estimator as RttEstimatorImpl).getBackedOffPto(1))
        assertEquals(basePto * 4, (estimator as RttEstimatorImpl).getBackedOffPto(2))
        assertEquals(basePto * 8, (estimator as RttEstimatorImpl).getBackedOffPto(3))
    }

    @Test
    fun `getBackedOffPto caps at MAX_PTO_BACKOFF`() {
        estimator.update(100_000_000L, 0)
        val basePto = estimator.getPto()

        val maxBackoff = 1L shl ReliableUdpConstants.MAX_PTO_BACKOFF // 2^6 = 64
        assertEquals(basePto * maxBackoff, (estimator as RttEstimatorImpl).getBackedOffPto(100))
    }

    @Test
    fun `update rejects zero RTT sample`() {
        assertThrows<IllegalArgumentException> {
            estimator.update(0, 0)
        }
    }

    @Test
    fun `update rejects negative RTT sample`() {
        assertThrows<IllegalArgumentException> {
            estimator.update(-1, 0)
        }
    }
}
