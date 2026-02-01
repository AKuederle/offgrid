package com.example.udpservice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for NetworkUtils.
 * Note: Actual network interface enumeration requires on-device testing.
 * These tests verify the filtering logic.
 */
@DisplayName("NetworkUtils")
class NetworkUtilsTest {

    @Test
    @DisplayName("filters out loopback addresses")
    fun `filters out loopback addresses`() {
        val addresses = listOf("127.0.0.1", "192.168.1.100", "::1")
        val filtered = NetworkUtils.filterValidAddresses(addresses)

        assertFalse(filtered.contains("127.0.0.1"))
        assertFalse(filtered.contains("::1"))
        assertTrue(filtered.contains("192.168.1.100"))
    }

    @Test
    @DisplayName("filters out link-local addresses")
    fun `filters out link local addresses`() {
        val addresses = listOf("169.254.1.1", "192.168.1.100", "fe80::1")
        val filtered = NetworkUtils.filterValidAddresses(addresses)

        assertFalse(filtered.contains("169.254.1.1"))
        assertFalse(filtered.contains("fe80::1"))
        assertTrue(filtered.contains("192.168.1.100"))
    }

    @Test
    @DisplayName("keeps valid IPv4 addresses")
    fun `keeps valid IPv4 addresses`() {
        val addresses = listOf("192.168.1.100", "10.0.0.1", "172.16.0.1")
        val filtered = NetworkUtils.filterValidAddresses(addresses)

        assertTrue(filtered.containsAll(addresses))
    }

    @Test
    @DisplayName("returns empty list when no valid addresses")
    fun `returns empty list when no valid addresses`() {
        val addresses = listOf("127.0.0.1", "::1", "169.254.1.1")
        val filtered = NetworkUtils.filterValidAddresses(addresses)

        assertTrue(filtered.isEmpty())
    }

    @Test
    @DisplayName("handles empty input")
    fun `handles empty input`() {
        val filtered = NetworkUtils.filterValidAddresses(emptyList())
        assertTrue(filtered.isEmpty())
    }
}
