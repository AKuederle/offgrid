package com.example.udpcli

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("UdpClient")
class UdpClientTest {

    @Nested
    @DisplayName("CliSendResult")
    inner class CliSendResultTests {

        @Test
        @DisplayName("Delivered contains messageId")
        fun `Delivered contains messageId`() {
            val result = CliSendResult.Delivered(42)
            assertEquals(42, result.messageId)
        }

        @Test
        @DisplayName("Failed contains messageId and reason")
        fun `Failed contains messageId and reason`() {
            val result = CliSendResult.Failed(1, "TIMEOUT")
            assertEquals(1, result.messageId)
            assertEquals("TIMEOUT", result.reason)
        }

        @Test
        @DisplayName("Timeout contains messageId")
        fun `Timeout contains messageId`() {
            val result = CliSendResult.Timeout(99)
            assertEquals(99, result.messageId)
        }
    }

    @Nested
    @DisplayName("CliReceivedMessage")
    inner class CliReceivedMessageTests {

        @Test
        @DisplayName("equals compares source and payload")
        fun `equals compares source and payload`() {
            val addr = java.net.InetSocketAddress("192.168.1.1", 5000)
            val msg1 = CliReceivedMessage(addr, "test".toByteArray(), 1000L)
            val msg2 = CliReceivedMessage(addr, "test".toByteArray(), 2000L)

            assertEquals(msg1, msg2) // Different timestamp, same content
        }

        @Test
        @DisplayName("not equals with different payload")
        fun `not equals with different payload`() {
            val addr = java.net.InetSocketAddress("192.168.1.1", 5000)
            val msg1 = CliReceivedMessage(addr, "test1".toByteArray(), 1000L)
            val msg2 = CliReceivedMessage(addr, "test2".toByteArray(), 1000L)

            assertNotEquals(msg1, msg2)
        }

        @Test
        @DisplayName("not equals with different source")
        fun `not equals with different source`() {
            val addr1 = java.net.InetSocketAddress("192.168.1.1", 5000)
            val addr2 = java.net.InetSocketAddress("192.168.1.2", 5000)
            val msg1 = CliReceivedMessage(addr1, "test".toByteArray(), 1000L)
            val msg2 = CliReceivedMessage(addr2, "test".toByteArray(), 1000L)

            assertNotEquals(msg1, msg2)
        }

        @Test
        @DisplayName("hashCode is consistent with equals")
        fun `hashCode is consistent with equals`() {
            val addr = java.net.InetSocketAddress("192.168.1.1", 5000)
            val msg1 = CliReceivedMessage(addr, "test".toByteArray(), 1000L)
            val msg2 = CliReceivedMessage(addr, "test".toByteArray(), 2000L)

            assertEquals(msg1.hashCode(), msg2.hashCode())
        }
    }

    @Nested
    @DisplayName("UdpClientImpl")
    inner class UdpClientImplTests {

        @Test
        @DisplayName("can be instantiated")
        fun `can be instantiated`() {
            val client = UdpClientImpl()
            assertNotNull(client)
            // Don't call any socket operations - just verify instantiation
            client.close()
        }

        @Test
        @DisplayName("close is idempotent")
        fun `close is idempotent`() {
            val client = UdpClientImpl()
            // Close without having bound - should not throw
            client.close()
            client.close()
        }

        @Test
        @DisplayName("close before any operations is safe")
        fun `close before any operations is safe`() {
            val client = UdpClientImpl()
            // Should not throw even though no socket was created
            assertDoesNotThrow { client.close() }
        }
    }
}
