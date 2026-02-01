package com.example.udpservice

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.udpservice.test.DeviceOnly
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3 Device-Only Tests: Real network interface detection.
 *
 * These tests verify behavior that differs between emulator and physical device.
 * They are excluded from CI runs via the @DeviceOnly annotation.
 *
 * Run with:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.udpservice.test.DeviceOnly
 */
@RunWith(AndroidJUnit4::class)
@DeviceOnly
class NetworkInfoDeviceTest {

    @Test
    fun detectsRealWifiIpAddress() {
        val addresses = NetworkUtils.getLocalIpAddresses()

        // On a real device with WiFi, we should get a real IP
        // This test will fail on emulator (gets 10.0.2.x virtual IP)
        assertTrue(
            "Expected real WiFi IP (192.168.x.x or 10.x.x.x), got: $addresses",
            addresses.any { addr ->
                addr.startsWith("192.168.") ||
                (addr.startsWith("10.") && !addr.startsWith("10.0.2."))
            }
        )
    }

    @Test
    fun doesNotReturnEmulatorVirtualIp() {
        val addresses = NetworkUtils.getLocalIpAddresses()

        // Emulator uses 10.0.2.x range - real device shouldn't have this
        assertFalse(
            "Got emulator virtual IP, expected real network: $addresses",
            addresses.any { it.startsWith("10.0.2.") }
        )
    }
}
