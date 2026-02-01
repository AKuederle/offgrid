package com.example.udpservice

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utilities for network information retrieval.
 */
object NetworkUtils {

    /**
     * Gets all valid local IP addresses (non-loopback, non-link-local IPv4).
     *
     * @return List of IP address strings suitable for display to the user.
     */
    fun getLocalIpAddresses(): List<String> {
        return try {
            val addresses = mutableListOf<String>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                for (address in networkInterface.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        addresses.add(address.hostAddress ?: continue)
                    }
                }
            }
            addresses
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Filters a list of IP address strings, removing loopback and link-local addresses.
     * Used for testing the filtering logic without actual network interfaces.
     *
     * @param addresses List of IP address strings to filter.
     * @return Filtered list containing only valid addresses.
     */
    fun filterValidAddresses(addresses: List<String>): List<String> {
        return addresses.filter { addr ->
            !isLoopback(addr) && !isLinkLocal(addr)
        }
    }

    private fun isLoopback(address: String): Boolean {
        return address.startsWith("127.") || address == "::1"
    }

    private fun isLinkLocal(address: String): Boolean {
        return address.startsWith("169.254.") || address.startsWith("fe80:")
    }
}
