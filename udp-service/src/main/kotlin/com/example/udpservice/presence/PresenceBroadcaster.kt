package com.example.udpservice.presence

import java.net.InetSocketAddress
import kotlin.time.Duration

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
 * Callback for peer activity events.
 *
 * Triggered when any activity (message or presence) is detected from a peer.
 */
typealias PeerActivityCallback = (peer: InetSocketAddress) -> Unit
