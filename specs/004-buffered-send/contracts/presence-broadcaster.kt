/**
 * Presence Broadcaster Contract
 *
 * Feature: 004-buffered-send
 * Purpose: Defines the interface for presence broadcast protocol
 */
package com.example.udpservice.presence

import java.net.InetSocketAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for presence broadcasting.
 */
data class PresenceConfig(
    /** Minimum interval between broadcasts to prevent storms */
    val minBroadcastInterval: Duration = 30.seconds,

    /** Port to use for broadcasts (same as main UDP port) */
    val broadcastPort: Int = 5000,

    /** Use subnet broadcast (true) or limited broadcast 255.255.255.255 (false) */
    val useSubnetBroadcast: Boolean = true
)

/**
 * Result of a broadcast operation.
 */
sealed class BroadcastResult {
    /** Broadcast sent successfully */
    object Success : BroadcastResult()

    /** Broadcast rate-limited, try again later */
    data class RateLimited(val waitMillis: Long) : BroadcastResult()

    /** Broadcast failed */
    data class Failed(val reason: String) : BroadcastResult()
}

/**
 * Interface for sending presence broadcasts.
 *
 * Responsibilities:
 * - Send presence announcements via UDP broadcast
 * - Rate limit broadcasts to prevent network flooding
 * - Detect local network broadcast address
 */
interface PresenceBroadcaster {
    /**
     * Get the current configuration.
     */
    val config: PresenceConfig

    /**
     * Send a presence broadcast to the local network.
     *
     * This announces that this device is online and ready to receive messages.
     * Peers that have pending messages for this device should resume delivery.
     *
     * Rate limited to prevent broadcast storms.
     *
     * @return BroadcastResult indicating success, rate-limited, or failure
     */
    suspend fun broadcast(): BroadcastResult

    /**
     * Get the time until next broadcast is allowed.
     *
     * @return Duration until next broadcast, or Duration.ZERO if allowed now
     */
    fun timeUntilNextBroadcast(): Duration

    /**
     * Get the detected broadcast address for the current network.
     *
     * @return Broadcast address, or null if not on a local network
     */
    fun getBroadcastAddress(): InetSocketAddress?
}

/**
 * Interface for receiving presence broadcasts.
 *
 * Responsibilities:
 * - Detect presence broadcasts from other peers
 * - Notify listeners when peer activity is detected
 */
interface PresenceListener {
    /**
     * Called when a presence broadcast is received from a peer.
     *
     * @param peer Source address of the broadcast
     */
    fun onPresenceReceived(peer: InetSocketAddress)
}

/**
 * Callback for peer activity events.
 *
 * Triggered when any activity (message or presence) is detected from a peer.
 */
typealias PeerActivityCallback = (peer: InetSocketAddress) -> Unit
