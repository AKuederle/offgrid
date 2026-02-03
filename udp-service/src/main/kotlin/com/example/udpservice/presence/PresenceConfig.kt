package com.example.udpservice.presence

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for presence broadcasting.
 *
 * @property minBroadcastInterval Minimum interval between broadcasts to prevent storms
 * @property broadcastPort Port to use for broadcasts (same as main UDP port)
 * @property useSubnetBroadcast Use subnet broadcast (true) or limited broadcast 255.255.255.255 (false)
 */
data class PresenceConfig(
    val minBroadcastInterval: Duration = 30.seconds,
    val broadcastPort: Int = 5000,
    val useSubnetBroadcast: Boolean = true
)
