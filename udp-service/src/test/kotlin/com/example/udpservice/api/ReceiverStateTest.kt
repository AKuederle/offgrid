package com.example.udpservice.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ReceiverState")
class ReceiverStateTest {

    @Nested
    @DisplayName("Stopped")
    inner class StoppedTests {
        @Test
        @DisplayName("should be a singleton (same instance)")
        fun `Stopped is a singleton`() {
            val first = ReceiverState.Stopped
            val second = ReceiverState.Stopped
            assertSame(first, second)
        }

        @Test
        @DisplayName("should be a ReceiverState subtype")
        fun `Stopped is ReceiverState subtype`() {
            val stopped: Any = ReceiverState.Stopped
            assertTrue(stopped is ReceiverState)
        }
    }

    @Nested
    @DisplayName("Starting")
    inner class StartingTests {
        @Test
        @DisplayName("should be a singleton (same instance)")
        fun `Starting is a singleton`() {
            val first = ReceiverState.Starting
            val second = ReceiverState.Starting
            assertSame(first, second)
        }

        @Test
        @DisplayName("should be a ReceiverState subtype")
        fun `Starting is ReceiverState subtype`() {
            val starting: Any = ReceiverState.Starting
            assertTrue(starting is ReceiverState)
        }
    }

    @Nested
    @DisplayName("Running")
    inner class RunningTests {
        @Test
        @DisplayName("should hold port correctly")
        fun `Running holds port correctly`() {
            val port = 8080
            val addresses = listOf("192.168.1.1")
            val running = ReceiverState.Running(port, addresses)
            assertEquals(port, running.port)
        }

        @Test
        @DisplayName("should hold addresses correctly")
        fun `Running holds addresses correctly`() {
            val port = 8080
            val addresses = listOf("192.168.1.1", "10.0.0.1")
            val running = ReceiverState.Running(port, addresses)
            assertEquals(addresses, running.addresses)
        }

        @Test
        @DisplayName("should hold empty addresses list")
        fun `Running holds empty addresses list`() {
            val port = 9000
            val addresses = emptyList<String>()
            val running = ReceiverState.Running(port, addresses)
            assertEquals(addresses, running.addresses)
        }

        @Test
        @DisplayName("should be a ReceiverState subtype")
        fun `Running is ReceiverState subtype`() {
            val running: Any = ReceiverState.Running(8080, listOf("127.0.0.1"))
            assertTrue(running is ReceiverState)
        }
    }

    @Nested
    @DisplayName("Error")
    inner class ErrorTests {
        @Test
        @DisplayName("should hold message correctly")
        fun `Error holds message correctly`() {
            val message = "Connection failed"
            val error = ReceiverState.Error(message)
            assertEquals(message, error.message)
        }

        @Test
        @DisplayName("should hold empty message")
        fun `Error holds empty message`() {
            val message = ""
            val error = ReceiverState.Error(message)
            assertEquals(message, error.message)
        }

        @Test
        @DisplayName("should be a ReceiverState subtype")
        fun `Error is ReceiverState subtype`() {
            val error: Any = ReceiverState.Error("Some error")
            assertTrue(error is ReceiverState)
        }
    }

    @Nested
    @DisplayName("All variants")
    inner class AllVariantsTests {
        @Test
        @DisplayName("all variants should be ReceiverState subtypes")
        fun `all variants are ReceiverState subtypes`() {
            val states: List<Any> = listOf(
                ReceiverState.Stopped,
                ReceiverState.Starting,
                ReceiverState.Running(8080, listOf("192.168.1.1")),
                ReceiverState.Error("Test error")
            )

            states.forEach { state ->
                assertTrue(state is ReceiverState, "Expected $state to be a ReceiverState")
            }
        }
    }
}
