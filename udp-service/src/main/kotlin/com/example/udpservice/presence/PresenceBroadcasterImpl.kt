package com.example.udpservice.presence

import com.example.reliableudp.protocol.Header
import com.example.reliableudp.protocol.PacketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of PresenceBroadcaster for sending presence broadcasts
 * to the local network.
 *
 * @property config Configuration for broadcast behavior
 * @property clock Function to get current time in milliseconds (for testing)
 */
class PresenceBroadcasterImpl(
    override val config: PresenceConfig = PresenceConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PresenceBroadcaster {

    private val lastBroadcastTime = AtomicLong(0)

    override suspend fun broadcast(): BroadcastResult {
        val now = clock()
        val lastTime = lastBroadcastTime.get()
        val elapsedMs = now - lastTime
        val minIntervalMs = config.minBroadcastInterval.inWholeMilliseconds

        // Check rate limit
        if (lastTime > 0 && elapsedMs < minIntervalMs) {
            val waitMs = minIntervalMs - elapsedMs
            return BroadcastResult.RateLimited(waitMs)
        }

        // Get broadcast address
        val broadcastAddress = getBroadcastAddress()
            ?: return BroadcastResult.Failed("No broadcast address available")

        // Build presence packet
        val packet = buildPresencePacket()

        return withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val datagram = DatagramPacket(
                        packet,
                        packet.size,
                        broadcastAddress.address,
                        broadcastAddress.port
                    )
                    socket.send(datagram)
                }

                // Update last broadcast time only on success
                lastBroadcastTime.set(clock())
                BroadcastResult.Success
            } catch (e: Exception) {
                BroadcastResult.Failed(e.message ?: "Unknown error")
            }
        }
    }

    override fun timeUntilNextBroadcast(): Duration {
        val now = clock()
        val lastTime = lastBroadcastTime.get()

        if (lastTime == 0L) {
            return Duration.ZERO
        }

        val elapsedMs = now - lastTime
        val minIntervalMs = config.minBroadcastInterval.inWholeMilliseconds

        return if (elapsedMs >= minIntervalMs) {
            Duration.ZERO
        } else {
            (minIntervalMs - elapsedMs).milliseconds
        }
    }

    override fun getBroadcastAddress(): InetSocketAddress? {
        return if (config.useSubnetBroadcast) {
            getSubnetBroadcastAddress()
        } else {
            // Limited broadcast (255.255.255.255)
            InetSocketAddress(InetAddress.getByName("255.255.255.255"), config.broadcastPort)
        }
    }

    /**
     * Get the subnet broadcast address for the active network interface.
     */
    private fun getSubnetBroadcastAddress(): InetSocketAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                    continue
                }

                // Look for a valid broadcast address
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast ?: continue

                    // Skip link-local addresses
                    if (interfaceAddress.address.isLinkLocalAddress) {
                        continue
                    }

                    // Skip IPv6 (broadcast is IPv4 only)
                    if (interfaceAddress.address.address.size != 4) {
                        continue
                    }

                    return InetSocketAddress(broadcast, config.broadcastPort)
                }
            }
        } catch (e: Exception) {
            // Fall through to null
        }

        return null
    }

    /**
     * Build a presence packet using the reliable-udp header format.
     */
    private fun buildPresencePacket(): ByteArray {
        val header = Header(
            type = PacketType.PRESENCE,
            messageId = 0,
            sequenceNumber = 0,
            fragmentIndex = 1,
            fragmentTotal = 1
        )
        return header.toBytes()
    }
}
