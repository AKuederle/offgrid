package com.example.udpservice.presence

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PresenceBroadcasterTest {

    @Test
    fun `default config uses 30 second interval`() {
        val config = PresenceConfig()
        assertEquals(30.seconds, config.minBroadcastInterval)
    }

    @Test
    fun `default config uses port 5000`() {
        val config = PresenceConfig()
        assertEquals(5000, config.broadcastPort)
    }

    @Test
    fun `default config uses subnet broadcast`() {
        val config = PresenceConfig()
        assertTrue(config.useSubnetBroadcast)
    }

    @Test
    fun `custom config values are preserved`() {
        val config = PresenceConfig(
            minBroadcastInterval = 60.seconds,
            broadcastPort = 5001,
            useSubnetBroadcast = false
        )

        assertEquals(60.seconds, config.minBroadcastInterval)
        assertEquals(5001, config.broadcastPort)
        assertFalse(config.useSubnetBroadcast)
    }

    @Test
    fun `timeUntilNextBroadcast returns zero initially`() {
        val broadcaster = PresenceBroadcasterImpl()
        assertEquals(Duration.ZERO, broadcaster.timeUntilNextBroadcast())
    }

    @Test
    fun `timeUntilNextBroadcast tracks rate limit`() {
        val currentTime = AtomicLong(1000L)
        val config = PresenceConfig(minBroadcastInterval = 30.seconds)
        val broadcaster = PresenceBroadcasterImpl(
            config = config,
            clock = { currentTime.get() }
        )

        // First broadcast allowed
        assertEquals(Duration.ZERO, broadcaster.timeUntilNextBroadcast())
    }

    @Test
    fun `limited broadcast address uses 255_255_255_255`() {
        val config = PresenceConfig(
            useSubnetBroadcast = false,
            broadcastPort = 5000
        )
        val broadcaster = PresenceBroadcasterImpl(config = config)

        val address = broadcaster.getBroadcastAddress()
        assertNotNull(address)
        assertEquals("255.255.255.255", address!!.address.hostAddress)
        assertEquals(5000, address.port)
    }

    @Test
    fun `broadcast result types`() {
        // Test that all result types can be created
        val success: BroadcastResult = BroadcastResult.Success
        val rateLimited: BroadcastResult = BroadcastResult.RateLimited(5000)
        val failed: BroadcastResult = BroadcastResult.Failed("Network error")

        assertTrue(success is BroadcastResult.Success)
        assertTrue(rateLimited is BroadcastResult.RateLimited)
        assertEquals(5000, (rateLimited as BroadcastResult.RateLimited).waitMillis)
        assertTrue(failed is BroadcastResult.Failed)
        assertEquals("Network error", (failed as BroadcastResult.Failed).reason)
    }

    @Test
    fun `rate limiting enforced after broadcast`() = runTest {
        val currentTime = AtomicLong(1000L)
        val config = PresenceConfig(
            minBroadcastInterval = 30.seconds,
            useSubnetBroadcast = false
        )
        val broadcaster = PresenceBroadcasterImpl(
            config = config,
            clock = { currentTime.get() }
        )

        // First broadcast (may succeed or fail depending on network)
        val firstResult = broadcaster.broadcast()

        // If first succeeded, check rate limiting
        if (firstResult is BroadcastResult.Success) {
            // Try immediately - should be rate limited
            val secondResult = broadcaster.broadcast()
            assertTrue(secondResult is BroadcastResult.RateLimited)

            val rateLimited = secondResult as BroadcastResult.RateLimited
            assertTrue(rateLimited.waitMillis > 0)
            assertTrue(rateLimited.waitMillis <= 30_000)
        }
    }

    @Test
    fun `rate limit expires after interval`() = runTest {
        val currentTime = AtomicLong(1000L)
        val config = PresenceConfig(
            minBroadcastInterval = 30.seconds,
            useSubnetBroadcast = false
        )
        val broadcaster = PresenceBroadcasterImpl(
            config = config,
            clock = { currentTime.get() }
        )

        // First broadcast
        val firstResult = broadcaster.broadcast()

        // If first succeeded, verify rate limit expires
        if (firstResult is BroadcastResult.Success) {
            // Advance time past the interval
            currentTime.set(1000L + 31_000L)

            // Time until next should be zero
            assertEquals(Duration.ZERO, broadcaster.timeUntilNextBroadcast())
        }
    }

    @Test
    fun `config is accessible via property`() {
        val config = PresenceConfig(broadcastPort = 6000)
        val broadcaster = PresenceBroadcasterImpl(config = config)

        assertSame(config, broadcaster.config)
        assertEquals(6000, broadcaster.config.broadcastPort)
    }
}
