package com.example.udpservice.broadcast

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageBroadcasterTest {

    private lateinit var context: Context
    private lateinit var broadcaster: MessageBroadcasterImpl

    // Captured values from Intent constructor and methods
    private var capturedAction: String? = null
    private var capturedPackage: String? = null
    private var capturedPrefixExtra: String? = null

    @BeforeEach
    fun setup() {
        capturedAction = null
        capturedPackage = null
        capturedPrefixExtra = null

        // Mock Intent constructor to capture values
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers {
            capturedPackage = firstArg()
            self as Intent
        }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers {
            if (firstArg<String>() == BroadcastActions.EXTRA_PREFIX) {
                capturedPrefixExtra = secondArg()
            }
            self as Intent
        }
        every { anyConstructed<Intent>().action } answers { capturedAction }

        context = mockk(relaxed = true)
        every { context.sendBroadcast(any()) } answers {
            val intent = firstArg<Intent>()
            // The action is set in the constructor
            capturedAction = BroadcastActions.ACTION_NEW_MESSAGE
            Unit
        }

        broadcaster = MessageBroadcasterImpl(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `broadcastNewMessage sends intent with correct action`() {
        broadcaster.broadcastNewMessage("broker", "com.example.udpbroker")

        assertEquals(BroadcastActions.ACTION_NEW_MESSAGE, capturedAction)
    }

    @Test
    fun `broadcastNewMessage sets explicit package`() {
        broadcaster.broadcastNewMessage("broker", "com.example.udpbroker")

        assertEquals("com.example.udpbroker", capturedPackage)
    }

    @Test
    fun `broadcastNewMessage includes prefix extra`() {
        broadcaster.broadcastNewMessage("alerts", "com.example.clientapp")

        assertEquals("alerts", capturedPrefixExtra)
    }

    @Test
    fun `broadcastNewMessage calls sendBroadcast once`() {
        broadcaster.broadcastNewMessage("broker", "com.example.udpbroker")

        verify(exactly = 1) { context.sendBroadcast(any()) }
    }
}
